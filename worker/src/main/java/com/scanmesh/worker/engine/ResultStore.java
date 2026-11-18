package com.scanmesh.worker.engine;

import com.scanmesh.worker.api.ClaimedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 엔진의 원본 결과 파일 위치를 정하고, 보관 기간이 지난 것을 지운다. 결과는 API에 저장되므로
 * 원본은 엔진 변환을 확인할 때만 쓴다. 시도마다 폴더를 나눠 재시도가 이전 결과를 덮어쓰지 않게 한다.
 */
@Slf4j
@Component
public class ResultStore {

    private static final String SCAN_PREFIX = "scan-";

    private final Path root;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public ResultStore(EngineProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ResultStore(EngineProperties properties, Clock clock) {
        this.root = properties.resultsDir().toAbsolutePath().normalize();
        this.retention = properties.resultsRetention();
        this.clock = clock;
    }

    public Path directoryFor(ClaimedTask task) {
        Path directory = root
            .resolve(SCAN_PREFIX + task.scanId())
            .resolve(task.engine().name().toLowerCase() + "-task-" + task.taskId() + "-attempt-" + task.attempt());
        try {
            return Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 점검 폴더 안의 가장 최근 파일도 보관 기간보다 오래됐으면 폴더째 지운다. 지우다 실패해도 점검은 계속한다.
     *
     * @return 지운 점검 폴더 수
     */
    public int deleteExpired() {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(retention);
        int deleted = 0;
        try (Stream<Path> scans = Files.list(root)) {
            for (Path scan : scans.filter(path -> path.getFileName().toString().startsWith(SCAN_PREFIX)).toList()) {
                if (lastModified(scan).isBefore(cutoff)) {
                    delete(scan);
                    deleted++;
                }
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("오래된 결과 파일을 지우지 못했습니다: {}", root, e);
        }
        if (deleted > 0) {
            log.info("보관 기간({})이 지난 결과 폴더 {}개를 지웠습니다.", retention, deleted);
        }
        return deleted;
    }

    private static Instant lastModified(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.map(ResultStore::modifiedTime).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
        }
    }

    private static Instant modifiedTime(Path path) {
        try {
            FileTime time = Files.getLastModifiedTime(path);
            return time.toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path directory) throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(directory)) {
            // 안쪽 파일부터 지워야 폴더가 비어서 지워진다
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
