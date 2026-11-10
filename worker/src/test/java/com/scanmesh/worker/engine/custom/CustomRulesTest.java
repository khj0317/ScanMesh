package com.scanmesh.worker.engine.custom;

import com.scanmesh.worker.engine.http.TargetFetcher.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRulesTest {

    private static final URI HTTPS = URI.create("https://example.com/");
    private static final URI HTTP = URI.create("http://example.com/");
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    private static final Map<String, List<String>> SAFE_HEADERS = Map.of(
        "strict-transport-security", List.of("max-age=31536000"),
        "content-security-policy", List.of("default-src 'self'; frame-ancestors 'none'"),
        "x-content-type-options", List.of("nosniff"),
        "server", List.of("nginx")
    );

    @Test
    void 설정이_잘_된_응답에서는_아무것도_찾지_않는다() {
        assertThat(ruleIds(HTTPS, SAFE_HEADERS)).isEmpty();
    }

    @Test
    void 헤더가_없으면_각각_찾아낸다() {
        assertThat(ruleIds(HTTPS, Map.of())).containsExactlyInAnyOrder(
            "MISSING_HSTS", "MISSING_CSP", "MISSING_X_CONTENT_TYPE_OPTIONS", "MISSING_FRAME_PROTECTION");
    }

    @Test
    void http로_제공되면_HSTS_대신_HTTPS_미사용을_알린다() {
        assertThat(ruleIds(HTTP, SAFE_HEADERS)).containsExactly("NO_HTTPS");
    }

    @Test
    void 버전이_보이는_서버_헤더만_알린다() {
        assertThat(ruleIds(HTTPS, with("server", "nginx/1.25.3"))).containsExactly("VERSION_DISCLOSURE");
    }

    @Test
    void 쿠키_속성을_검사하고_값은_근거에_남기지_않는다() {
        List<Finding> findings = ResponseRules.evaluate(HTTPS, response(with("set-cookie", "session=secret-value; Path=/")), NOW);

        assertThat(findings).extracting(Finding::ruleId)
            .containsExactlyInAnyOrder("COOKIE_NO_SECURE", "COOKIE_NO_HTTPONLY", "COOKIE_NO_SAMESITE");
        assertThat(findings).allSatisfy(finding -> {
            assertThat(finding.evidence()).isEqualTo("쿠키: session");
        });
        assertThat(ruleIds(HTTPS, with("set-cookie", "session=x; Secure; HttpOnly; SameSite=Lax"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "/.env        | DB_PASSWORD=hunter2",
        "/.git/HEAD   | ref: refs/heads/main",
        "/.git/config | [core]",
    })
    void 내용이_맞으면_민감_파일로_판단한다(String path, String body) {
        assertThat(file(path).matches(body)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "/.env        | <!doctype html><html>APP_NAME=shop</html>",
        "/.git/HEAD   | <html>not found</html>",
        "/.env        | just some text",
    })
    void SPA_첫_화면이나_다른_내용은_민감_파일로_보지_않는다(String path, String body) {
        assertThat(file(path).matches(body)).isFalse();
    }

    private static SensitiveFile file(String path) {
        return SensitiveFile.ALL.stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow();
    }

    private static Map<String, List<String>> with(String name, String value) {
        var headers = new java.util.HashMap<>(SAFE_HEADERS);
        headers.put(name, List.of(value));
        return headers;
    }

    private static List<String> ruleIds(URI url, Map<String, List<String>> headers) {
        return ResponseRules.evaluate(url, response(headers), NOW).stream().map(Finding::ruleId).toList();
    }

    private static Response response(Map<String, List<String>> headers) {
        return new Response(200, headers, "", null);
    }
}
