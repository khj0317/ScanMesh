package com.scanmesh.worker.engine.custom;

/**
 * 자체 규칙이 찾은 문제 하나.
 *
 * @param severity CRITICAL, HIGH, MEDIUM, LOW, INFO
 * @param cweId    해당하는 CWE 번호
 * @param evidence 사용자가 확인할 수 있는 근거. 비밀 값이 그대로 담기지 않게 규칙에서 줄인다
 */
public record Finding(String ruleId, String title, String severity, int cweId, String url, String evidence) {
}
