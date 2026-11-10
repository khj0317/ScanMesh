package com.scanmesh.worker.engine.container;

import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.engine.EngineProperties;
import com.scanmesh.worker.engine.TaskContext;
import com.scanmesh.worker.engine.http.TargetFetcher;
import com.scanmesh.worker.engine.http.TargetFetcherFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 점검 엔진을 Docker 컨테이너로 실행하고 끝날 때까지 지켜본다.
 * 취소되거나 시간을 넘기거나 대상이 응답하지 않으면 컨테이너를 지운다. docker 명령만 끝내면 컨테이너는 계속 돌기 때문이다.
 */
@Slf4j
@Component
public class ContainerRunner {

    private static final Duration POLL = Duration.ofSeconds(1);
    /** 재시작 중이거나 과부하인 게이트웨이 응답은 대상이 응답하지 않는 것으로 본다 */
    private static final Set<Integer> UNHEALTHY_STATUSES = Set.of(502, 503, 504);
    private static final int MAX_PROGRESS_BEFORE_FINISH = 95;

    /** 컨테이너 실행 내용 */
    public record Spec(String image, List<String> mounts, List<String> arguments, Duration timeout, Duration expectedDuration, Path logFile) {
    }

    private final Docker docker;
    private final EngineProperties properties;
    private final TargetFetcherFactory fetchers;

    @Autowired
    public ContainerRunner(EngineProperties properties, TargetFetcherFactory fetchers) {
        this(new ProcessDocker(), properties, fetchers);
    }

    ContainerRunner(Docker docker, EngineProperties properties, TargetFetcherFactory fetchers) {
        this.docker = docker;
        this.properties = properties;
        this.fetchers = fetchers;
    }

    /** @return 컨테이너 종료 코드 */
    public int run(Spec spec, ClaimedTask task, TaskContext context) throws IOException, InterruptedException {
        String name = "scanmesh-task-" + task.taskId() + "-" + task.attempt();
        Docker.Container container = docker.run(command(name, spec, task, context), spec.logFile());
        boolean finished = false;
        try (TargetFetcher probe = fetchers.create(task.host(), context.pinnedAddress().orElse(null))) {
            long startedAt = System.nanoTime();
            long nextProbeAt = startedAt + properties.probeInterval().toNanos();
            int failures = 0;
            while (!container.waitFor(POLL)) {
                if (context.isCancelled()) {
                    throw new InterruptedException("취소됨");
                }
                long now = System.nanoTime();
                Duration elapsed = Duration.ofNanos(now - startedAt);
                if (elapsed.compareTo(spec.timeout()) > 0) {
                    throw new IOException("엔진이 제한 시간(" + spec.timeout() + ") 안에 끝나지 않았습니다.");
                }
                context.reportProgress(estimate(elapsed, spec.expectedDuration()));
                if (now >= nextProbeAt) {
                    failures = isResponding(probe, task) ? 0 : failures + 1;
                    if (failures >= properties.probeFailures()) {
                        throw new IOException("대상이 " + failures + "번 연달아 응답하지 않아 점검을 멈췄습니다.");
                    }
                    nextProbeAt = now + properties.probeInterval().toNanos();
                }
            }
            finished = true;
            return container.exitCode();
        } finally {
            if (!finished) {
                log.info("컨테이너 중지: {}", name);
                docker.remove(name);
            }
        }
    }

    private List<String> command(String name, Spec spec, ClaimedTask task, TaskContext context) {
        List<String> command = new ArrayList<>(List.of("docker", "run", "--rm", "--name", name));
        context.pinnedAddress().ifPresent(address -> {
            // 엔진이 DNS를 다시 조회하지 않고 검사를 통과한 IP로 연결하게 한다
            command.add("--add-host");
            command.add(task.host() + ":" + address);
        });
        if (properties.dockerNetwork() != null && !properties.dockerNetwork().isBlank()) {
            command.add("--network");
            command.add(properties.dockerNetwork());
        }
        for (String mount : spec.mounts()) {
            command.add("-v");
            command.add(mount);
        }
        command.add(spec.image());
        command.addAll(spec.arguments());
        return command;
    }

    private boolean isResponding(TargetFetcher probe, ClaimedTask task) {
        try {
            return !UNHEALTHY_STATUSES.contains(probe.get(URI.create(task.targetUrl())).status());
        } catch (IOException e) {
            log.info("대상 응답 없음: task={}, {}", task.taskId(), e.toString());
            return false;
        }
    }

    /** 엔진이 진행률을 알려 주지 않으므로 0주차에 잰 실행 시간을 기준으로 어림한다 */
    private static int estimate(Duration elapsed, Duration expected) {
        long percent = elapsed.toMillis() * 100 / Math.max(1, expected.toMillis());
        return (int) Math.min(percent, MAX_PROGRESS_BEFORE_FINISH);
    }

    /** docker 명령 실행. 테스트에서 실제 컨테이너 대신 바꿔 끼운다 */
    interface Docker {

        Container run(List<String> command, Path logFile) throws IOException;

        void remove(String name);

        interface Container {

            /** @return 끝났으면 true */
            boolean waitFor(Duration duration) throws InterruptedException;

            int exitCode();
        }
    }
}
