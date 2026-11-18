package com.scanmesh.worker.engine.custom;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.worker.engine.http.TargetFetcher;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** 다른 패키지의 테스트가 자체 규칙의 응답 검사만 따로 돌려 볼 수 있게 열어 둔다 */
public final class CustomRulesProbe {

    private CustomRulesProbe() {
    }

    public static List<Finding> evaluate(URI url, TargetFetcher.Response response) {
        return ResponseRules.evaluate(url, response, Instant.now());
    }
}
