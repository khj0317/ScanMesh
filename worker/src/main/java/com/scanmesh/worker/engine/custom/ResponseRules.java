package com.scanmesh.worker.engine.custom;

import com.scanmesh.worker.engine.http.TargetFetcher.Response;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 첫 화면 응답 하나로 판단하는 규칙: 보안 헤더, 쿠키 설정, 인증서 만료, 버전 노출 */
final class ResponseRules {

    private static final Duration CERTIFICATE_WARNING = Duration.ofDays(30);
    private static final Pattern HAS_VERSION = Pattern.compile("\\d+\\.\\d+");

    private ResponseRules() {
    }

    static List<Finding> evaluate(URI url, Response response, Instant now) {
        List<Finding> findings = new ArrayList<>();
        boolean https = "https".equalsIgnoreCase(url.getScheme());
        checkTransport(url, response, https, now, findings);
        checkHeaders(url, response, findings);
        checkCookies(url, response, https, findings);
        return findings;
    }

    private static void checkTransport(URI url, Response response, boolean https, Instant now, List<Finding> findings) {
        if (!https) {
            findings.add(new Finding("NO_HTTPS", "HTTPS를 쓰지 않음", "MEDIUM", 319, url.toString(), "최종 응답이 http로 제공됩니다."));
            return;
        }
        if (!response.hasHeader("strict-transport-security")) {
            findings.add(missing("MISSING_HSTS", "Strict-Transport-Security 헤더 없음", "LOW", 319, url));
        }
        X509Certificate certificate = response.certificate();
        if (certificate != null) {
            Duration remaining = Duration.between(now, certificate.getNotAfter().toInstant());
            if (remaining.compareTo(CERTIFICATE_WARNING) < 0) {
                findings.add(new Finding("TLS_CERT_EXPIRING", "TLS 인증서 만료 임박", "MEDIUM", 298, url.toString(),
                    "만료일: " + certificate.getNotAfter().toInstant()));
            }
        }
    }

    private static void checkHeaders(URI url, Response response, List<Finding> findings) {
        String csp = String.join(";", response.headerValues("content-security-policy"));
        if (csp.isEmpty()) {
            findings.add(missing("MISSING_CSP", "Content-Security-Policy 헤더 없음", "MEDIUM", 693, url));
        }
        String contentTypeOptions = response.firstHeader("x-content-type-options");
        if (contentTypeOptions == null || !contentTypeOptions.strip().equalsIgnoreCase("nosniff")) {
            findings.add(missing("MISSING_X_CONTENT_TYPE_OPTIONS", "X-Content-Type-Options: nosniff 없음", "LOW", 693, url));
        }
        if (!response.hasHeader("x-frame-options") && !csp.toLowerCase(Locale.ROOT).contains("frame-ancestors")) {
            findings.add(missing("MISSING_FRAME_PROTECTION", "클릭재킹 방어 헤더 없음", "MEDIUM", 1021, url));
        }
        for (String header : List.of("server", "x-powered-by")) {
            String value = response.firstHeader(header);
            if (value != null && HAS_VERSION.matcher(value).find()) {
                findings.add(new Finding("VERSION_DISCLOSURE", "서버 소프트웨어 버전 노출", "INFO", 200, url.toString(), header + ": " + value));
            }
        }
    }

    private static void checkCookies(URI url, Response response, boolean https, List<Finding> findings) {
        for (String cookie : response.headerValues("set-cookie")) {
            String name = cookie.split("=", 2)[0].strip();
            String attributes = cookie.toLowerCase(Locale.ROOT);
            // 쿠키 값은 세션 정보일 수 있으므로 이름만 근거로 남긴다
            String evidence = "쿠키: " + name;
            if (https && !attributes.contains("; secure") && !attributes.contains(";secure")) {
                findings.add(new Finding("COOKIE_NO_SECURE", "쿠키에 Secure 속성 없음", "MEDIUM", 614, url.toString(), evidence));
            }
            if (!attributes.contains("httponly")) {
                findings.add(new Finding("COOKIE_NO_HTTPONLY", "쿠키에 HttpOnly 속성 없음", "LOW", 1004, url.toString(), evidence));
            }
            if (!attributes.contains("samesite")) {
                findings.add(new Finding("COOKIE_NO_SAMESITE", "쿠키에 SameSite 속성 없음", "LOW", 1275, url.toString(), evidence));
            }
        }
    }

    private static Finding missing(String ruleId, String title, String severity, int cweId, URI url) {
        return new Finding(ruleId, title, severity, cweId, url.toString(), "응답에 해당 헤더가 없습니다.");
    }
}
