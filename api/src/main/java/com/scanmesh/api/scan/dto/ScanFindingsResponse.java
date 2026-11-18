package com.scanmesh.api.scan.dto;

import java.util.List;
import java.util.Map;

/**
 * 점검 결과. 같은 검사에 걸린 여러 페이지를 문제(issue) 하나로 접고, 엔진이 달라도 같은 페이지의 같은 문제는 위치 하나로 묶는다.
 * ZAP은 같은 경고를 페이지마다 따로 내므로, 접지 않으면 같은 문제가 수십 줄로 늘어난다.
 *
 * @param status  점검이 아직 끝나지 않았으면 끝난 엔진의 결과만 들어 있다
 * @param summary 위험도별 문제 수
 */
public record ScanFindingsResponse(Long scanId, String status, Map<String, Long> summary, int locationCount, List<Issue> issues) {

    /**
     * @param check     검사 종류. 엔진이 함께 하는 검사는 엔진과 상관없이 같은 값이다
     * @param severity  위치들 중 가장 높은 위험도
     * @param engines   이 문제를 찾은 엔진. 여러 엔진이 함께 찾았으면 더 믿을 만하다
     * @param locations 위험도가 높은 것부터
     */
    public record Issue(
        String check,
        String title,
        String severity,
        Integer cweId,
        String owaspCategory,
        List<String> engines,
        String remediation,
        List<Location> locations
    ) {
    }

    /** @param occurrences 엔진별로 저장된, 묶이기 전 항목 수 */
    public record Location(
        String fingerprint,
        String url,
        String param,
        String severity,
        List<String> engines,
        String evidence,
        int occurrences
    ) {
    }
}
