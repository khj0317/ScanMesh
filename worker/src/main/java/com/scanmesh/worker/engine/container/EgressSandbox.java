package com.scanmesh.worker.engine.container;

import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.egress.EgressProxy;
import com.scanmesh.worker.engine.EngineProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 엔진 컨테이너가 점검 대상 외의 곳으로 나가지 못하게 가둔다.
 * <ul>
 *   <li>엔진은 바깥으로 나갈 수 없는 Docker 내부 네트워크(--internal)에서만 돈다.</li>
 *   <li>작업마다 출구 프록시(EgressProxy) 컨테이너를 하나 띄워 그 네트워크와 바깥에 함께 붙인다.
 *       프록시는 대상 호스트로만, 워커가 검사해 고정한 IP로만 연결한다.</li>
 * </ul>
 * 프록시 이미지는 워커 안의 EgressProxy 클래스 파일로 만든다. 클래스가 바뀌면 이미지 태그(내용 해시)도 바뀌어 다시 만든다.
 */
@Slf4j
@Component
public class EgressSandbox {

    static final String BASE_IMAGE = "eclipse-temurin:21-jre";
    private static final String CLASS_RESOURCE = "/com/scanmesh/worker/egress/EgressProxy.class";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    /** 처음에는 기반 이미지를 내려받으므로 넉넉히 둔다 */
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(20);
    /** 고정 IP가 없는 개발용 대상의 이름을 엔진 안에서 풀 때 쓰는 주소(문서용, 어디로도 가지 않음). 실제 연결은 프록시가 한다 */
    private static final String UNROUTABLE = "192.0.2.1";

    private final ContainerRunner.Docker docker;
    private final EngineProperties properties;
    private String image;

    @Autowired
    public EgressSandbox(EngineProperties properties) {
        this(new ProcessDocker(), properties);
    }

    EgressSandbox(ContainerRunner.Docker docker, EngineProperties properties) {
        this.docker = docker;
        this.properties = properties;
    }

    public static String proxyName(ClaimedTask task) {
        return "scanmesh-proxy-" + task.taskId() + "-" + task.attempt();
    }

    /** 엔진에 넘길 프록시 주소. 엔진과 같은 내부 네트워크에서 컨테이너 이름으로 찾는다 */
    public static String proxyUrl(ClaimedTask task) {
        return "http://" + proxyName(task) + ":" + EgressProxy.PORT;
    }

    /** 엔진 컨테이너에 붙일 네트워크 설정. 내부 네트워크에만 붙이고, 대상 이름은 고정 IP(없으면 갈 곳 없는 주소)로 풀리게 한다 */
    public List<String> engineNetworkOptions(ClaimedTask task, String pinnedAddress) {
        return List.of(
            "--network", properties.egressNetwork(),
            "--add-host", task.host() + ":" + (pinnedAddress == null ? UNROUTABLE : pinnedAddress)
        );
    }

    public void startProxy(ClaimedTask task, String pinnedAddress) throws IOException, InterruptedException {
        String image = prepare();
        String name = proxyName(task);
        URI target = URI.create(task.targetUrl());
        int port = target.getPort() != -1 ? target.getPort() : "https".equalsIgnoreCase(target.getScheme()) ? 443 : 80;
        // 개발용 대상(Juice Shop 등)이 다른 Docker 네트워크에 있으면 프록시를 그 네트워크로 내보낸다
        String outbound = properties.dockerNetwork() == null || properties.dockerNetwork().isBlank() ? "bridge" : properties.dockerNetwork();
        check(docker.exec(List.of("docker", "run", "-d", "--rm", "--name", name, "--network", outbound, "--memory", "128m",
            image, task.host(), String.valueOf(port), pinnedAddress == null ? "-" : pinnedAddress), COMMAND_TIMEOUT), "출구 프록시 시작");
        try {
            check(docker.exec(List.of("docker", "network", "connect", properties.egressNetwork(), name), COMMAND_TIMEOUT), "출구 프록시 연결");
            waitReady(name);
        } catch (IOException | InterruptedException | RuntimeException e) {
            stopProxy(task);
            throw e;
        }
    }

    public void stopProxy(ClaimedTask task) {
        docker.remove(proxyName(task));
    }

    /** 내부 네트워크와 프록시 이미지가 없으면 만든다. @return 프록시 이미지 이름 */
    synchronized String prepare() throws IOException, InterruptedException {
        if (image != null) {
            return image;
        }
        String network = properties.egressNetwork();
        if (docker.exec(List.of("docker", "network", "inspect", network), COMMAND_TIMEOUT).exitCode() != 0) {
            log.info("엔진용 내부 네트워크 만들기: {}", network);
            check(docker.exec(List.of("docker", "network", "create", "--internal", network), COMMAND_TIMEOUT), "내부 네트워크 만들기");
        }
        byte[] proxyClass = proxyClass();
        String tag = "scanmesh-egress-proxy:" + HexFormat.of().formatHex(sha256(proxyClass)).substring(0, 12);
        if (docker.exec(List.of("docker", "image", "inspect", tag), COMMAND_TIMEOUT).exitCode() != 0) {
            log.info("출구 프록시 이미지 만들기: {}", tag);
            build(tag, proxyClass);
        }
        image = tag;
        return image;
    }

    private void build(String tag, byte[] proxyClass) throws IOException, InterruptedException {
        Path context = Files.createTempDirectory("scanmesh-egress-proxy");
        try {
            Path classFile = context.resolve("com/scanmesh/worker/egress/EgressProxy.class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, proxyClass);
            Files.writeString(context.resolve("Dockerfile"), """
                FROM %s
                COPY com /app/com
                USER 10001
                ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-cp", "/app", "com.scanmesh.worker.egress.EgressProxy"]
                """.formatted(BASE_IMAGE));
            check(docker.exec(List.of("docker", "build", "-t", tag, context.toString()), BUILD_TIMEOUT), "출구 프록시 이미지 만들기");
        } finally {
            try (var files = Files.walk(context)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private void waitReady(String name) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ContainerRunner.Docker.ExecResult logs = docker.exec(List.of("docker", "logs", name), COMMAND_TIMEOUT);
            if (logs.output().contains(EgressProxy.READY)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IOException("출구 프록시가 " + READY_TIMEOUT.toSeconds() + "초 안에 준비되지 않았습니다: " + name);
    }

    private static void check(ContainerRunner.Docker.ExecResult result, String step) throws IOException {
        if (result.exitCode() != 0) {
            throw new IOException(step + " 실패 (종료 코드 " + result.exitCode() + "): " + result.output().strip());
        }
    }

    private static byte[] proxyClass() throws IOException {
        try (InputStream in = EgressSandbox.class.getResourceAsStream(CLASS_RESOURCE)) {
            if (in == null) {
                throw new IOException("EgressProxy 클래스 파일을 찾을 수 없습니다.");
            }
            return in.readAllBytes();
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
