package com.scanmesh.worker.engine.custom;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.Severity;
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
    private static final String MISSING = "응답에 해당 헤더가 없습니다.";

    private ResponseRules() {
    }

    static List<Finding> evaluate(URI url, Response response, Instant now) {
        List<Finding> findings = new ArrayList<>();
        boolean https = "https".equalsIgnoreCase(url.getScheme());
        String page = url.toString();
        checkTransport(page, response, https, now, findings);
        checkHeaders(page, response, findings);
        checkCookies(page, response, https, findings);
        return findings;
    }

    private static void checkTransport(String url, Response response, boolean https, Instant now, List<Finding> findings) {
        if (!https) {
            findings.add(new Finding("NO_HTTPS", "HTTPS를 쓰지 않음", Severity.MEDIUM, 319, url, null,
                "최종 응답이 http로 제공됩니다.", "TLS 인증서를 설치하고 http 요청을 https로 리다이렉트하세요."));
            return;
        }
        if (!response.hasHeader("strict-transport-security")) {
            findings.add(new Finding("MISSING_HSTS", "Strict-Transport-Security 헤더 없음", Severity.LOW, 319, url, null, MISSING,
                "Strict-Transport-Security: max-age=31536000; includeSubDomains 헤더를 보내 브라우저가 https로만 접속하게 하세요."));
        }
        X509Certificate certificate = response.certificate();
        if (certificate != null) {
            Duration remaining = Duration.between(now, certificate.getNotAfter().toInstant());
            if (remaining.compareTo(CERTIFICATE_WARNING) < 0) {
                findings.add(new Finding("TLS_CERT_EXPIRING", "TLS 인증서 만료 임박", Severity.MEDIUM, 298, url, null,
                    "만료일: " + certificate.getNotAfter().toInstant(), "인증서를 갱신하고 자동 갱신을 설정하세요."));
            }
        }
    }

    private static void checkHeaders(String url, Response response, List<Finding> findings) {
        String csp = String.join(";", response.headerValues("content-security-policy"));
        if (csp.isEmpty()) {
            findings.add(new Finding("MISSING_CSP", "Content-Security-Policy 헤더 없음", Severity.MEDIUM, 693, url, null, MISSING,
                "스크립트를 불러올 수 있는 출처를 제한하는 Content-Security-Policy 헤더를 보내세요."));
        }
        String contentTypeOptions = response.firstHeader("x-content-type-options");
        if (contentTypeOptions == null || !contentTypeOptions.strip().equalsIgnoreCase("nosniff")) {
            findings.add(new Finding("MISSING_X_CONTENT_TYPE_OPTIONS", "X-Content-Type-Options: nosniff 없음", Severity.LOW, 693, url, null, MISSING,
                "X-Content-Type-Options: nosniff 헤더를 보내 브라우저가 파일 형식을 추측하지 않게 하세요."));
        }
        if (!response.hasHeader("x-frame-options") && !csp.toLowerCase(Locale.ROOT).contains("frame-ancestors")) {
            findings.add(new Finding("MISSING_FRAME_PROTECTION", "클릭재킹 방어 헤더 없음", Severity.MEDIUM, 1021, url, null, MISSING,
                "Content-Security-Policy의 frame-ancestors나 X-Frame-Options 헤더로 다른 사이트가 이 페이지를 프레임에 넣지 못하게 하세요."));
        }
        for (String header : List.of("server", "x-powered-by")) {
            String value = response.firstHeader(header);
            if (value != null && HAS_VERSION.matcher(value).find()) {
                findings.add(new Finding("VERSION_DISCLOSURE", "서버 소프트웨어 버전 노출", Severity.INFO, 200, url, header,
                    header + ": " + value, "응답 헤더에서 소프트웨어 이름과 버전을 빼세요."));
            }
        }
    }

    private static void checkCookies(String url, Response response, boolean https, List<Finding> findings) {
        for (String cookie : response.headerValues("set-cookie")) {
            String name = cookie.split("=", 2)[0].strip();
            String attributes = cookie.toLowerCase(Locale.ROOT);
            // 쿠키 값은 세션 정보일 수 있으므로 이름만 위치로 남긴다
            String evidence = "쿠키: " + name;
            if (https && !attributes.contains("; secure") && !attributes.contains(";secure")) {
                findings.add(new Finding("COOKIE_NO_SECURE", "쿠키에 Secure 속성 없음", Severity.MEDIUM, 614, url, name, evidence,
                    "Secure 속성을 붙여 쿠키가 https로만 전송되게 하세요."));
            }
            if (!attributes.contains("httponly")) {
                findings.add(new Finding("COOKIE_NO_HTTPONLY", "쿠키에 HttpOnly 속성 없음", Severity.LOW, 1004, url, name, evidence,
                    "HttpOnly 속성을 붙여 스크립트가 쿠키를 읽지 못하게 하세요."));
            }
            if (!attributes.contains("samesite")) {
                findings.add(new Finding("COOKIE_NO_SAMESITE", "쿠키에 SameSite 속성 없음", Severity.LOW, 1275, url, name, evidence,
                    "SameSite=Lax 이상을 붙여 다른 사이트에서 보낸 요청에 쿠키가 실리지 않게 하세요."));
            }
        }
    }
}
