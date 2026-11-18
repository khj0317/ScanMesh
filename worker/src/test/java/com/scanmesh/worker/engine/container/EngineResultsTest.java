package com.scanmesh.worker.engine.container;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.FindingFingerprint;
import com.scanmesh.common.finding.Severity;
import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.engine.custom.CustomRulesProbe;
import com.scanmesh.worker.engine.http.TargetFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 0주차·4주차에 Juice Shop을 점검해서 받은 실제 결과의 형식을 본떠 만든 예제로 확인한다 */
class EngineResultsTest {

    @TempDir
    Path dir;

    @Test
    void Nuclei_결과를_매처까지_구분해서_바꾼다() throws Exception {
        Path file = dir.resolve("nuclei.jsonl");
        Files.writeString(file, """
            {"template-id":"http-missing-security-headers","info":{"name":"HTTP Missing Security Headers","severity":"info","classification":{"cve-id":null,"cwe-id":["cwe-693"]},"remediation":"Add the header"},"matcher-name":"content-security-policy","matched-at":"http://example.com/","request":"GET / HTTP/1.1"}

            {"template-id":"owasp-juice-shop-detect","info":{"name":"OWASP Juice Shop - Detect","severity":"info"},"matched-at":"http://example.com/","extracted-results":["17.1.1"]}
            {"template-id":"CVE-2099-0001","info":{"name":"Example RCE","severity":"critical","classification":{"cwe-id":["cwe-78"]}},"url":"http://example.com/api"}
            """);

        List<Finding> findings = NucleiResults.parse(file, 500);

        assertThat(findings).hasSize(3);
        Finding header = findings.getFirst();
        assertThat(header.ruleId()).isEqualTo("http-missing-security-headers:content-security-policy");
        assertThat(header.title()).isEqualTo("HTTP Missing Security Headers (content-security-policy)");
        assertThat(header.cweId()).isEqualTo(693);
        assertThat(header.remediation()).isEqualTo("Add the header");
        assertThat(findings.get(1).evidence()).isEqualTo("17.1.1");
        assertThat(findings.get(1).cweId()).isNull();
        assertThat(findings.get(2).severity()).isEqualTo(Severity.CRITICAL);
        assertThat(findings.get(2).url()).isEqualTo("http://example.com/api");
    }

    @Test
    void Nuclei가_아무것도_찾지_못해_파일이_없으면_빈_목록이다() throws Exception {
        assertThat(NucleiResults.parse(dir.resolve("nuclei.jsonl"), 500)).isEmpty();
    }

    @Test
    void ZAP_경고를_URL마다_나누고_조치_방법의_HTML을_뗀다() throws Exception {
        Path file = dir.resolve("zap.json");
        Files.writeString(file, """
            {"@programName":"ZAP","site":[{"@name":"http://example.com","alerts":[
              {"pluginid":"10038","alertRef":"10038-1","alert":"Content Security Policy (CSP) Header Not Set","riskcode":"2","cweid":"693",
               "solution":"<p>Set the header.</p><p>See docs.</p>",
               "instances":[{"uri":"http://example.com/","param":"","evidence":""},{"uri":"http://example.com/login","param":"","evidence":""}]},
              {"pluginid":"10109","alertRef":"10109","alert":"Modern Web Application","riskcode":"0","cweid":"-1","solution":"",
               "instances":[{"uri":"http://example.com/","param":"","evidence":"<script src=\\"main.js\\">"}]},
              {"pluginid":"10010","alertRef":"10010","alert":"Cookie No HttpOnly Flag","riskcode":"1","cweid":"1004","solution":"<p>Add HttpOnly.</p>",
               "instances":[{"uri":"http://example.com/","param":"token","evidence":"Set-Cookie: token"}]}
            ]}]}
            """);

        List<Finding> findings = ZapReport.parse(file, 500);

        assertThat(findings).extracting(Finding::ruleId).containsExactly("10038-1", "10038-1", "10109", "10010");
        assertThat(findings).extracting(Finding::url).containsExactly(
            "http://example.com/", "http://example.com/login", "http://example.com/", "http://example.com/");
        assertThat(findings.getFirst().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(findings.getFirst().remediation()).isEqualTo("Set the header.\nSee docs.");
        assertThat(findings.getFirst().evidence()).isNull();
        assertThat(findings.get(2).cweId()).isNull();
        assertThat(findings.get(2).severity()).isEqualTo(Severity.INFO);
        assertThat(findings.get(3).param()).isEqualTo("token");
    }

    /**
     * 보안 헤더 없이 버전을 드러내고 속성 없는 쿠키를 주는 시험 페이지(http://127.0.0.1:8000/)를 ZAP 2.17로 점검한 실제 보고서다.
     * 같은 응답을 자체 규칙으로 검사한 결과와 지문이 겹쳐야, 결과 화면에서 같은 문제가 한 번만 보인다.
     */
    @Test
    void 실제_ZAP_보고서와_자체_규칙의_같은_문제는_지문이_같다() throws Exception {
        Path report = Path.of(getClass().getResource("/zap/header-less-site.json").toURI());
        Set<String> zap = ZapReport.parse(report, 500).stream()
            .map(finding -> FindingFingerprint.of(Engine.ZAP, finding))
            .collect(Collectors.toSet());

        var response = new TargetFetcher.Response(200, Map.of(
            "set-cookie", List.of("sid=abc123; Path=/"),
            "server", List.of("Apache/2.4.41 (Ubuntu)"),
            "x-powered-by", List.of("PHP/8.1.2")
        ), "", null);
        List<Finding> custom = CustomRulesProbe.evaluate(URI.create("http://127.0.0.1:8000/"), response);

        // http라 HSTS 대신 HTTPS 미사용이 나오고, 이것은 ZAP baseline이 하지 않는 검사다
        assertThat(custom).filteredOn(finding -> !finding.ruleId().equals("NO_HTTPS"))
            .allSatisfy(finding -> assertThat(zap)
                .as(finding.ruleId() + " " + finding.param())
                .contains(FindingFingerprint.of(Engine.CUSTOM, finding)));
        assertThat(custom).extracting(Finding::ruleId).contains(
            "MISSING_CSP", "MISSING_FRAME_PROTECTION", "MISSING_X_CONTENT_TYPE_OPTIONS",
            "VERSION_DISCLOSURE", "COOKIE_NO_HTTPONLY", "COOKIE_NO_SAMESITE");
    }

    @Test
    void ZAP_경고_하나에_URL이_많으면_20개까지만_남기고_전체도_제한한다() throws Exception {
        String instances = IntStream.range(0, 30)
            .mapToObj(i -> "{\"uri\":\"http://example.com/p" + i + "\"}")
            .collect(Collectors.joining(","));
        String alert = "{\"alertRef\":\"%s\",\"alert\":\"A\",\"riskcode\":\"1\",\"cweid\":\"16\",\"instances\":[" + instances + "]}";
        Path file = dir.resolve("zap.json");
        Files.writeString(file, "{\"site\":[{\"alerts\":[" + alert.formatted("1") + "," + alert.formatted("2") + "]}]}");

        assertThat(ZapReport.parse(file, 500)).hasSize(40);
        assertThat(ZapReport.parse(file, 25)).hasSize(25);
    }
}
