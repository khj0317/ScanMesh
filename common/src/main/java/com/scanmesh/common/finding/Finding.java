package com.scanmesh.common.finding;

/**
 * 엔진마다 다른 결과 형식을 바꿔 담는 공통 발견 항목. 워커가 만들어 작업 완료 보고에 실어 보내고 API가 저장한다.
 *
 * @param ruleId      엔진 안에서의 규칙 ID (ZAP alertRef, Nuclei 템플릿 ID:매처 이름, 자체 규칙 ID)
 * @param cweId       CWE 번호. 엔진이 알려 주지 않으면 null
 * @param param       같은 URL에서 문제가 생긴 곳을 나누는 이름 (쿠키 이름, 파라미터 이름). 없으면 null
 * @param evidence    근거. 비밀 값이 그대로 담기지 않게 엔진 변환 단계에서 줄인다
 * @param remediation 조치 방법. 없으면 null
 */
public record Finding(
    String ruleId,
    String title,
    Severity severity,
    Integer cweId,
    String url,
    String param,
    String evidence,
    String remediation
) {
}
