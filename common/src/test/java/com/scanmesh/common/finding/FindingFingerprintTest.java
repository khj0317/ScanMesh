package com.scanmesh.common.finding;

import com.scanmesh.common.scan.Engine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindingFingerprintTest {

    @Test
    void 엔진이_달라도_같은_검사를_같은_페이지에서_찾으면_지문이_같다() {
        String custom = FindingFingerprint.of(Engine.CUSTOM, finding("MISSING_CSP", "https://Example.com:443/", null));
        String zap = FindingFingerprint.of(Engine.ZAP, finding("10038-1", "https://example.com/?utm=1#top", null));
        String nuclei = FindingFingerprint.of(Engine.NUCLEI, finding("http-missing-security-headers:content-security-policy", "https://example.com", null));

        assertThat(zap).isEqualTo(custom);
        assertThat(nuclei).isEqualTo(custom);
    }

    @Test
    void 페이지나_쿠키가_다르면_따로_본다() {
        String home = FindingFingerprint.of(Engine.CUSTOM, finding("MISSING_CSP", "https://example.com/", null));
        String other = FindingFingerprint.of(Engine.CUSTOM, finding("MISSING_CSP", "https://example.com/login", null));
        String session = FindingFingerprint.of(Engine.CUSTOM, finding("COOKIE_NO_HTTPONLY", "https://example.com/", "session"));
        String theme = FindingFingerprint.of(Engine.ZAP, finding("10010", "https://example.com/", "Theme"));
        String sessionFromZap = FindingFingerprint.of(Engine.ZAP, finding("10010", "https://example.com/", "SESSION"));

        assertThat(other).isNotEqualTo(home);
        assertThat(theme).isNotEqualTo(session);
        assertThat(sessionFromZap).isEqualTo(session);
    }

    @Test
    void 표에_없는_규칙은_엔진마다_따로_본다() {
        assertThat(FindingFingerprint.of(Engine.ZAP, finding("10098", "https://example.com/", null)))
            .isNotEqualTo(FindingFingerprint.of(Engine.NUCLEI, finding("10098", "https://example.com/", null)));
    }

    @Test
    void CWE를_OWASP_분류로_바꾼다() {
        assertThat(OwaspTop10.categoryOf(319)).startsWith("A02");
        assertThat(OwaspTop10.categoryOf(1021)).startsWith("A04");
        assertThat(OwaspTop10.categoryOf(614)).startsWith("A05");
        assertThat(OwaspTop10.categoryOf(693)).isNull();
        assertThat(OwaspTop10.categoryOf(null)).isNull();
    }

    @Test
    void 보안_헤더_문제는_보충_규칙으로_A05에_넣는다() {
        assertThat(OwaspTop10.categoryOf("missing-hsts", 319)).startsWith("A05");
        assertThat(OwaspTop10.categoryOf("ZAP:90004-2", 693)).startsWith("A05");
        assertThat(OwaspTop10.categoryOf("ZAP:10098", 264)).startsWith("A01");
        assertThat(OwaspTop10.categoryOf("ZAP:10109", null)).isNull();
    }

    @Test
    void ZAP이_위치에_헤더_이름을_적어도_자체_규칙과_묶인다() {
        String custom = FindingFingerprint.of(Engine.CUSTOM, finding("MISSING_FRAME_PROTECTION", "http://example.com/", null));
        String zap = FindingFingerprint.of(Engine.ZAP, finding("10020-1", "http://example.com/", "x-frame-options"));

        assertThat(zap).isEqualTo(custom);
    }

    @Test
    void 버전_노출은_헤더별로_나눠서_묶는다() {
        String customServer = FindingFingerprint.of(Engine.CUSTOM, finding("VERSION_DISCLOSURE", "http://example.com/", "server"));
        String customPoweredBy = FindingFingerprint.of(Engine.CUSTOM, finding("VERSION_DISCLOSURE", "http://example.com/", "x-powered-by"));

        assertThat(FindingFingerprint.of(Engine.ZAP, finding("10036-2", "http://example.com/", null))).isEqualTo(customServer);
        assertThat(FindingFingerprint.of(Engine.ZAP, finding("10037", "http://example.com/", null))).isEqualTo(customPoweredBy);
        assertThat(customServer).isNotEqualTo(customPoweredBy);
    }

    @Test
    void 엔진_위험도를_공통_위험도로_바꾼다() {
        assertThat(Severity.fromZapRiskCode("3")).isEqualTo(Severity.HIGH);
        assertThat(Severity.fromZapRiskCode("0")).isEqualTo(Severity.INFO);
        assertThat(Severity.fromNuclei("Critical")).isEqualTo(Severity.CRITICAL);
        assertThat(Severity.fromNuclei("unknown")).isEqualTo(Severity.INFO);
        assertThat(Severity.HIGH.isHigherThan(Severity.LOW)).isTrue();
    }

    private static Finding finding(String ruleId, String url, String param) {
        return new Finding(ruleId, "t", Severity.LOW, null, url, param, null, null);
    }
}
