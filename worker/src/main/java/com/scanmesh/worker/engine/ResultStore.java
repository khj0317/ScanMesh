package com.scanmesh.worker.engine;

import com.scanmesh.worker.api.ClaimedTask;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 엔진 결과 파일의 위치를 정한다. 시도마다 폴더를 나눠서 재시도가 이전 결과를 덮어쓰지 않게 한다 */
@Component
public class ResultStore {

    private final Path root;

    public ResultStore(EngineProperties properties) {
        this.root = properties.resultsDir().toAbsolutePath().normalize();
    }

    public Path directoryFor(ClaimedTask task) {
        Path directory = root
            .resolve("scan-" + task.scanId())
            .resolve(task.engine().name().toLowerCase() + "-task-" + task.taskId() + "-attempt-" + task.attempt());
        try {
            return Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
