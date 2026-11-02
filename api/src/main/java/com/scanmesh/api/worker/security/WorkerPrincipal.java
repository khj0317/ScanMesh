package com.scanmesh.api.worker.security;

import com.scanmesh.common.scan.WorkerKind;

/** 워커 토큰으로 인증된 워커 */
public record WorkerPrincipal(Long workerId, WorkerKind kind) {
}
