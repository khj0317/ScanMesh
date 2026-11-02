package com.scanmesh.api.worker.dto;

/** @param token 워커 설정(WORKER_TOKEN)에 넣을 값. 다시 볼 수 없으므로 잃어버리면 워커를 새로 등록한다 */
public record WorkerCreatedResponse(Long id, String name, String kind, String token) {
}
