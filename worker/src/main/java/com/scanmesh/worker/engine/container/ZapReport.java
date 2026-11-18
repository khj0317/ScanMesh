package com.scanmesh.worker.engine.container;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.Severity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ZAP의 JSON 보고서를 공통 발견 항목으로 바꾼다. 경고 하나에 문제가 생긴 URL(instance)이 여러 개 달려 있어서,
 * 다른 엔진의 결과와 페이지 단위로 묶이도록 URL마다 항목 하나로 나눈다.
 */
final class ZapReport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** 같은 경고가 페이지마다 반복되면 끝없이 늘어나므로 경고 하나에서 이만큼만 남긴다 */
    private static final int MAX_INSTANCES_PER_ALERT = 20;
    private static final int MAX_EVIDENCE = 500;
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private ZapReport() {
    }

    static List<Finding> parse(Path file, int limit) throws IOException {
        JsonNode report = JSON.readTree(Files.readString(file));
        List<Finding> findings = new ArrayList<>();
        for (JsonNode site : report.path("site")) {
            for (JsonNode alert : site.path("alerts")) {
                String ruleId = alert.path("alertRef").asString(alert.path("pluginid").asString(""));
                String title = alert.path("alert").asString(alert.path("name").asString(ruleId));
                Severity severity = Severity.fromZapRiskCode(alert.path("riskcode").asString(""));
                Integer cweId = cwe(alert.path("cweid").asString(""));
                String remediation = plainText(alert.path("solution").asString(""));
                int used = 0;
                for (JsonNode instance : alert.path("instances")) {
                    if (used++ >= MAX_INSTANCES_PER_ALERT || findings.size() >= limit) {
                        break;
                    }
                    findings.add(new Finding(
                        ruleId,
                        title,
                        severity,
                        cweId,
                        instance.path("uri").asString(""),
                        blankToNull(instance.path("param").asString("")),
                        truncate(blankToNull(instance.path("evidence").asString(""))),
                        remediation
                    ));
                }
            }
        }
        return findings;
    }

    /** ZAP은 CWE가 없으면 -1을 준다 */
    private static Integer cwe(String value) {
        try {
            int id = Integer.parseInt(value.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 조치 방법이 <p>…</p> HTML로 오므로 태그를 떼어 문단을 줄바꿈으로 바꾼다 */
    private static String plainText(String html) {
        String text = TAG.matcher(html.replace("</p>", "\n")).replaceAll("").strip();
        return text.isEmpty() ? null : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_EVIDENCE ? value : value.substring(0, MAX_EVIDENCE);
    }
}
