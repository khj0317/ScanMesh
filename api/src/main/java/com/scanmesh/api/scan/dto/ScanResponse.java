package com.scanmesh.api.scan.dto;

import com.scanmesh.api.scan.entity.Scan;
import com.scanmesh.api.scan.entity.ScanTask;

import java.time.Instant;
import java.util.List;

public record ScanResponse(
    Long id,
    Long targetId,
    String baseUrl,
    String status,
    int progress,
    Instant requestedAt,
    Instant finishedAt,
    List<Task> tasks
) {

    public record Task(
        String engine,
        String status,
        int progress,
        int attempt,
        int maxAttempts,
        boolean cancelRequested,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
    ) {

        static Task from(ScanTask task) {
            return new Task(
                task.getEngine().name(),
                task.getStatus().name(),
                task.getProgress(),
                task.getAttempt(),
                task.getMaxAttempts(),
                task.isCancelRequested(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getFinishedAt()
            );
        }
    }

    public static ScanResponse from(Scan scan) {
        return new ScanResponse(
            scan.getId(),
            scan.getTarget().getId(),
            scan.getTarget().getBaseUrl(),
            scan.status().name(),
            scan.progress(),
            scan.getRequestedAt(),
            scan.getFinishedAt(),
            scan.getTasks().stream().map(Task::from).toList()
        );
    }
}
