package com.scanmesh.worker.engine.container;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.Severity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Nuclei의 JSONL 결과(한 줄에 하나)를 공통 발견 항목으로 바꾼다 */
final class NucleiResults {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CWE = Pattern.compile("(?i)cwe-(\\d+)");
    private static final int MAX_EVIDENCE = 500;

    private NucleiResults() {
    }

    /** 발견한 것이 없으면 Nuclei가 파일을 만들지 않으므로 빈 목록 */
    static List<Finding> parse(Path file, int limit) throws IOException {
        List<Finding> findings = new ArrayList<>();
        if (!Files.exists(file)) {
            return findings;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && findings.size() < limit) {
                if (!line.isBlank()) {
                    findings.add(toFinding(JSON.readTree(line)));
                }
            }
        }
        return findings;
    }

    private static Finding toFinding(JsonNode result) {
        JsonNode info = result.path("info");
        String templateId = result.path("template-id").asString("");
        String matcher = text(result, "matcher-name");
        // 같은 템플릿이라도 매처마다 다른 문제다 (예: 빠진 보안 헤더가 매처마다 하나씩)
        String ruleId = matcher == null ? templateId : templateId + ":" + matcher;
        String title = info.path("name").asString(templateId) + (matcher == null ? "" : " (" + matcher + ")");
        String url = text(result, "matched-at");
        return new Finding(
            ruleId,
            title,
            Severity.fromNuclei(text(info, "severity")),
            cwe(info.path("classification").path("cwe-id")),
            url == null ? text(result, "url") : url,
            null,
            evidence(result, matcher),
            text(info, "remediation")
        );
    }

    private static Integer cwe(JsonNode cweIds) {
        for (JsonNode id : cweIds) {
            Matcher matcher = CWE.matcher(id.asString(""));
            if (matcher.find()) {
                return Integer.valueOf(matcher.group(1));
            }
        }
        return null;
    }

    private static String evidence(JsonNode result, String matcher) {
        List<String> extracted = new ArrayList<>();
        for (JsonNode value : result.path("extracted-results")) {
            extracted.add(value.asString(""));
        }
        String evidence = extracted.isEmpty() ? (matcher == null ? null : "일치한 항목: " + matcher) : String.join(", ", extracted);
        return evidence == null || evidence.length() <= MAX_EVIDENCE ? evidence : evidence.substring(0, MAX_EVIDENCE);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }
}
