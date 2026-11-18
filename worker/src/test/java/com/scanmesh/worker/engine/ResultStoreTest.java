package com.scanmesh.worker.engine;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ResultStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

    @TempDir
    Path root;

    @Test
    void 보관_기간이_지난_점검_폴더만_지운다() throws Exception {
        ResultStore store = new ResultStore(EngineTestSupport.properties(root, Duration.ofSeconds(15), 4), Clock.fixed(NOW, ZoneOffset.UTC));
        Path old = writeResult(store, 1L, NOW.minus(Duration.ofDays(8)));
        Path recent = writeResult(store, 2L, NOW.minus(Duration.ofDays(1)));
        Files.writeString(root.resolve("notes.txt"), "점검 폴더가 아니면 건드리지 않는다");
        Files.setLastModifiedTime(root.resolve("notes.txt"), FileTime.from(NOW.minus(Duration.ofDays(30))));

        assertThat(store.deleteExpired()).isEqualTo(1);

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(root.resolve("notes.txt")).exists();
    }

    @Test
    void 폴더가_아직_없으면_아무것도_하지_않는다() {
        ResultStore store = new ResultStore(EngineTestSupport.properties(root.resolve("missing"), Duration.ofSeconds(15), 4));

        assertThat(store.deleteExpired()).isZero();
    }

    /** @return 점검 폴더 */
    private static Path writeResult(ResultStore store, long scanId, Instant modified) throws Exception {
        Path directory = store.directoryFor(new ClaimedTask(scanId * 10, scanId, Engine.ZAP, "https://example.com/", "example.com", 1, 90, 30));
        Path file = Files.writeString(directory.resolve("zap.json"), "{}");
        Files.setLastModifiedTime(file, FileTime.from(modified));
        Files.setLastModifiedTime(directory, FileTime.from(modified));
        Files.setLastModifiedTime(directory.getParent(), FileTime.from(modified));
        return directory.getParent();
    }
}
