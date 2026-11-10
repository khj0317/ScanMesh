package com.scanmesh.worker.engine.container;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.engine.EngineProperties;
import com.scanmesh.worker.engine.EngineRunner;
import com.scanmesh.worker.engine.ResultStore;
import com.scanmesh.worker.engine.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Nuclei로 알려진 CVE와 설정 실수를 찾는다. 결과는 nuclei.jsonl (발견한 것이 없으면 파일이 없다) */
@Slf4j
@Component
@RequiredArgsConstructor
public class NucleiEngine implements EngineRunner {

    /** 0주차 측정: -as -rl 50 -c 10 으로 253초 */
    private static final Duration EXPECTED = Duration.ofMinutes(5);

    private final ContainerRunner containers;
    private final ResultStore results;
    private final EngineProperties properties;

    @Override
    public Engine engine() {
        return Engine.NUCLEI;
    }

    @Override
    public void run(ClaimedTask task, TaskContext context) throws IOException, InterruptedException {
        Path directory = results.directoryFor(task);
        ContainerRunner.Spec spec = new ContainerRunner.Spec(
            properties.nucleiImage(),
            List.of(directory + ":/results", properties.nucleiTemplatesVolume() + ":/root/nuclei-templates"),
            List.of(
                "-u", task.targetUrl(),
                "-jsonl", "-o", "/results/nuclei.jsonl",
                // 0주차에 제한 없이 돌리자 대상 앱이 메모리 부족으로 죽었다. 맞는 템플릿만 골라 속도를 제한해서 돌린다
                "-as", "-rl", "50", "-c", "10",
                // 사용자가 소유한 사이트라도 서비스를 해치거나 데이터를 바꿀 수 있는 템플릿은 돌리지 않는다
                "-etags", "dos,fuzz,intrusive,bruteforce",
                "-silent", "-disable-update-check"
            ),
            properties.nucleiTimeout(),
            EXPECTED,
            directory.resolve("nuclei.log")
        );
        int exitCode = containers.run(spec, task, context);
        if (exitCode != 0) {
            throw new IOException("Nuclei가 종료 코드 " + exitCode + "로 끝났습니다. 로그: " + spec.logFile());
        }
        log.info("Nuclei 완료: task={}, 결과 폴더={}", task.taskId(), directory);
    }
}
