package com.scanmesh.worker.engine.container;

import com.scanmesh.worker.engine.EngineProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * ZAP baseline은 실행할 때마다 인터넷에서 확장 규칙(pscanrulesBeta)을 받아 온다. 점검 중인 엔진은 바깥과 끊겨 있어서 받지 못하고,
 * 그러면 교차 출처 정책·캐시·위험한 JS 함수 같은 규칙이 빠진다. 그래서 대상과 상관없는 준비 단계에서 일반 네트워크로 규칙을 받아
 * 이미지로 만들어 두고, 점검은 그 이미지로 한다. 규칙이 새로 나오는 것을 따라가도록 주마다 다시 만든다.
 */
@Slf4j
@Component
public class ZapImage {

    private static final String REPOSITORY = "scanmesh-zap";
    private static final Duration PREPARE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final ContainerRunner.Docker docker;
    private final EngineProperties properties;
    private final Clock clock;

    @Autowired
    public ZapImage(EngineProperties properties) {
        this(new ProcessDocker(), properties, Clock.systemDefaultZone());
    }

    ZapImage(ContainerRunner.Docker docker, EngineProperties properties, Clock clock) {
        this.docker = docker;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return 이번 주의 확장 규칙이 들어 있는 ZAP 이미지. 없으면 만든다 */
    public synchronized String prepare() throws IOException, InterruptedException {
        LocalDate today = LocalDate.now(clock);
        String tag = REPOSITORY + ":" + today.get(IsoFields.WEEK_BASED_YEAR) + "-w" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (docker.exec(List.of("docker", "image", "inspect", tag), COMMAND_TIMEOUT).exitCode() == 0) {
            return tag;
        }
        log.info("ZAP 확장 규칙 받기: {}", tag);
        String container = "scanmesh-zap-prepare-" + System.nanoTime();
        try {
            // 대상에는 닿지 않는 준비 단계라 일반 네트워크에서 받는다
            check(docker.exec(List.of("docker", "run", "--name", container, properties.zapImage(),
                "zap.sh", "-cmd", "-addonupdate", "-addoninstall", "pscanrulesBeta"), PREPARE_TIMEOUT), "ZAP 확장 규칙 받기");
            check(docker.exec(List.of("docker", "commit", container, tag), PREPARE_TIMEOUT), "ZAP 이미지 저장");
        } finally {
            docker.remove(container);
        }
        removeOlderWeeks(tag);
        return tag;
    }

    /** 이미지 하나가 4GB 남짓이라 지난주 것을 남겨 두면 디스크가 금방 찬다 */
    private void removeOlderWeeks(String current) throws IOException, InterruptedException {
        ContainerRunner.Docker.ExecResult images = docker.exec(
            List.of("docker", "images", REPOSITORY, "--format", "{{.Repository}}:{{.Tag}}"), COMMAND_TIMEOUT);
        for (String image : images.output().strip().split("\\s+")) {
            if (image.startsWith(REPOSITORY + ":") && !image.equals(current)) {
                log.info("지난 ZAP 이미지 지우기: {}", image);
                docker.exec(List.of("docker", "rmi", image), COMMAND_TIMEOUT);
            }
        }
    }

    private static void check(ContainerRunner.Docker.ExecResult result, String step) throws IOException {
        if (result.exitCode() != 0) {
            String output = result.output().strip();
            throw new IOException(step + " 실패 (종료 코드 " + result.exitCode() + "): " + output.substring(Math.max(0, output.length() - 500)));
        }
    }
}
