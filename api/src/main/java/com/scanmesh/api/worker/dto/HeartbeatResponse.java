package com.scanmesh.api.worker.dto;

import java.time.Instant;

/** @param cancelRequested true면 워커는 엔진을 멈추고 fail로 보고한다. 서버가 취소로 기록한다 */
public record HeartbeatResponse(boolean cancelRequested, Instant leaseUntil) {
}
