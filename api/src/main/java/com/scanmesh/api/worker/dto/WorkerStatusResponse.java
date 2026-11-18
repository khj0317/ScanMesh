package com.scanmesh.api.worker.dto;

/**
 * 사용자 화면에 보여 줄 워커 상태. 워커 이름이나 개수는 알리지 않고, 종류별로 하나라도 켜져 있는지만 준다.
 *
 * @param lightOnline 자체 규칙·Nuclei를 돌리는 워커
 * @param heavyOnline ZAP을 돌리는 워커
 */
public record WorkerStatusResponse(boolean lightOnline, boolean heavyOnline) {
}
