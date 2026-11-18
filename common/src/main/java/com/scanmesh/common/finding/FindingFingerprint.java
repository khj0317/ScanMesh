package com.scanmesh.common.finding;

import com.scanmesh.common.scan.Engine;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 엔진이 달라도 같은 문제면 같은 값이 나오는 지문. 점검 결과를 보여 줄 때 이 값으로 묶어서 중복을 없앤다.
 * 검사 종류(엔진 간에 같은 검사는 같은 이름) + 정규화한 URL + 위치(쿠키 이름 등)로 만든다.
 */
public final class FindingFingerprint {

    /**
     * 여러 엔진이 함께 하는 검사를 같은 이름으로 묶는다. 표에 없는 규칙은 "엔진:규칙 ID"가 그대로 이름이 된다.
     * ZAP 값은 ZAP 2.17의 실제 보고서에서 확인했다(worker 테스트의 zap/header-less-site.json). 단, HSTS(10035-1)와
     * 쿠키 Secure(10011)는 https에서만 나와 확인하지 못했고, Nuclei 값은 템플릿 문서를 따랐다.
     * 규칙 ID가 바뀌면 묶이지 않고 따로 보일 뿐, 결과가 사라지지는 않는다.
     */
    private static final Map<String, String> SHARED_CHECKS = Map.ofEntries(
        Map.entry("CUSTOM:MISSING_CSP", "missing-csp"),
        Map.entry("ZAP:10038-1", "missing-csp"),
        Map.entry("NUCLEI:http-missing-security-headers:content-security-policy", "missing-csp"),

        Map.entry("CUSTOM:MISSING_HSTS", "missing-hsts"),
        Map.entry("ZAP:10035-1", "missing-hsts"),
        Map.entry("NUCLEI:http-missing-security-headers:strict-transport-security", "missing-hsts"),

        Map.entry("CUSTOM:MISSING_X_CONTENT_TYPE_OPTIONS", "missing-x-content-type-options"),
        Map.entry("ZAP:10021", "missing-x-content-type-options"),
        Map.entry("NUCLEI:http-missing-security-headers:x-content-type-options", "missing-x-content-type-options"),

        Map.entry("CUSTOM:MISSING_FRAME_PROTECTION", "missing-frame-protection"),
        Map.entry("ZAP:10020-1", "missing-frame-protection"),
        Map.entry("NUCLEI:http-missing-security-headers:x-frame-options", "missing-frame-protection"),

        // 자체 규칙은 헤더 이름을 위치(param)로 남기므로 아래 checkName에서 헤더별로 나눈다
        Map.entry("ZAP:10036-1", "version-disclosure:server"),
        Map.entry("ZAP:10036-2", "version-disclosure:server"),
        Map.entry("ZAP:10037", "version-disclosure:x-powered-by"),

        Map.entry("CUSTOM:COOKIE_NO_HTTPONLY", "cookie-no-httponly"),
        Map.entry("ZAP:10010", "cookie-no-httponly"),
        Map.entry("CUSTOM:COOKIE_NO_SECURE", "cookie-no-secure"),
        Map.entry("ZAP:10011", "cookie-no-secure"),
        Map.entry("CUSTOM:COOKIE_NO_SAMESITE", "cookie-no-samesite"),
        Map.entry("ZAP:10054-1", "cookie-no-samesite")
    );

    private FindingFingerprint() {
    }

    public static String of(Engine engine, Finding finding) {
        String check = checkName(engine, finding.ruleId(), finding.param());
        return sha256(check + "|" + normalizeUrl(finding.url()) + "|" + location(check, finding.param()));
    }

    /**
     * 검사 종류. 결과를 보여 줄 때 같은 검사의 여러 위치를 하나로 접는 데도 쓴다.
     *
     * @param param 자체 규칙의 버전 노출은 헤더 이름이 여기 들어 있어 검사를 나누는 데 쓴다
     */
    public static String checkName(Engine engine, String ruleId, String param) {
        if (engine == Engine.CUSTOM && "VERSION_DISCLOSURE".equals(ruleId)) {
            return "version-disclosure:" + normalize(param);
        }
        String key = engine.name() + ":" + ruleId;
        return SHARED_CHECKS.getOrDefault(key, key);
    }

    /**
     * 같은 URL 안에서 위치를 나누는 값. 엔진이 함께 하는 검사는 위치를 적는 방식이 엔진마다 달라서
     * (ZAP은 빠진 헤더 이름을 적고 자체 규칙은 비워 둔다) 쿠키 검사만 쿠키 이름으로 나눈다.
     */
    private static String location(String check, String param) {
        boolean shared = SHARED_CHECKS.containsValue(check) || check.startsWith("version-disclosure:");
        if (shared && !check.startsWith("cookie-")) {
            return "";
        }
        return normalize(param);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /** 스킴·호스트 소문자, 기본 포트 생략, 쿼리와 # 조각 제거. 같은 페이지를 엔진마다 조금씩 다르게 적어도 묶이게 한다 */
    static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(url.strip());
            if (uri.getHost() == null) {
                return url.strip();
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = port == -1 || scheme.equals("http") && port == 80 || scheme.equals("https") && port == 443;
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port) + path;
        } catch (Exception e) {
            return url.strip();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
