package com.scanmesh.api.worker.dto;

import com.scanmesh.api.worker.entity.Worker;

import java.time.Instant;

public record WorkerResponse(Long id, String name, String kind, Instant lastSeenAt, boolean online) {

    public static WorkerResponse from(Worker worker, Instant onlineSince) {
        boolean online = worker.getLastSeenAt() != null && worker.getLastSeenAt().isAfter(onlineSince);
        return new WorkerResponse(worker.getId(), worker.getName(), worker.getKind().name(), worker.getLastSeenAt(), online);
    }
}
