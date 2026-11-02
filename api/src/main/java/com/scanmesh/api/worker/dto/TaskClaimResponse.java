package com.scanmesh.api.worker.dto;

/**
 * 워커가 가져간 작업.
 *
 * @param heartbeatIntervalSeconds 이 간격으로 하트비트를 보내야 임대가 끝나지 않는다
 */
public record TaskClaimResponse(
    Long taskId,
    Long scanId,
    String engine,
    String targetUrl,
    String host,
    int attempt,
    long leaseSeconds,
    long heartbeatIntervalSeconds
) {
}
