package com.scanmesh.worker.api;

import com.scanmesh.common.scan.Engine;

/** API에서 가져온 작업. 필드 이름은 API의 TaskClaimResponse와 같다 */
public record ClaimedTask(
    Long taskId,
    Long scanId,
    Engine engine,
    String targetUrl,
    String host,
    int attempt,
    long leaseSeconds,
    long heartbeatIntervalSeconds
) {
}
