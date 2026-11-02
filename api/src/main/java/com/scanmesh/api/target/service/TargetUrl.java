package com.scanmesh.api.target.service;

import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 사용자가 입력한 점검 대상 주소를 검사하고 정규화한 결과.
 * IP 주소 대신 도메인만 받는다. IP는 DNS TXT로 소유를 증명할 수 없고, 내부망 주소를 넣는 가장 쉬운 길이기도 하다.
 * 도메인이 실제로 어떤 IP를 가리키는지는 여기서 보지 않고, 등록·인증·점검 때마다 DNS를 조회해서 따로 검사한다.
 */
public record TargetUrl(String baseUrl, String host) {

    private static final int MAX_LENGTH = 2048;
    private static final Pattern LABEL = Pattern.compile("^(?!-)[a-z0-9-]{1,63}(?<!-)$");
    private static final Pattern NUMERIC_HOST = Pattern.compile("^[0-9.]+$");
    /** 공용 DNS에 없거나 내부용으로 쓰이는 이름 */
    private static final List<String> RESERVED_SUFFIXES = List.of(
        "localhost", "local", "internal", "intranet", "lan", "home", "corp", "localdomain",
        "home.arpa", "arpa", "test", "example", "invalid", "onion"
    );

    public static TargetUrl parse(String input) {
        String raw = input == null ? "" : input.strip();
        if (raw.isEmpty() || raw.length() > MAX_LENGTH) {
            throw invalid("주소를 " + MAX_LENGTH + "자 이내로 입력해주세요.");
        }

        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw invalid("주소 형식이 올바르지 않습니다.");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw invalid("http:// 또는 https://로 시작하는 주소를 입력해주세요.");
        }
        if (uri.getRawUserInfo() != null) {
            // https://victim.com@evil.com 처럼 사람 눈과 실제 접속 대상이 달라지는 주소를 막는다
            throw invalid("주소에 아이디나 비밀번호(@)를 넣을 수 없습니다.");
        }

        String host = normalizeHost(uri);
        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw invalid("포트 번호가 올바르지 않습니다.");
        }
        boolean defaultPort = port == -1
            || scheme.equals("http") && port == 80
            || scheme.equals("https") && port == 443;

        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        // 쿼리와 # 조각은 점검 시작 주소에 필요 없고, 같은 사이트가 다른 주소로 여러 번 등록되는 원인이 된다
        String baseUrl = scheme + "://" + host + (defaultPort ? "" : ":" + port) + path;
        return new TargetUrl(baseUrl, host);
    }

    private static String normalizeHost(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            // 한글 도메인은 java.net.URI가 호스트로 읽지 못하므로 authority에서 직접 꺼낸다
            host = hostFromAuthority(uri.getRawAuthority());
        }
        if (host.startsWith("[") || NUMERIC_HOST.matcher(host).matches()) {
            throw invalid("IP 주소 대신 도메인 이름으로 등록해주세요.");
        }

        String ascii;
        try {
            ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw invalid("도메인 형식이 올바르지 않습니다.");
        }
        if (ascii.endsWith(".")) {
            ascii = ascii.substring(0, ascii.length() - 1);
        }
        if (ascii.length() > 253) {
            throw invalid("도메인이 너무 깁니다.");
        }

        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2) {
            throw invalid("example.com처럼 점(.)이 들어간 도메인을 입력해주세요.");
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                throw invalid("도메인 형식이 올바르지 않습니다.");
            }
        }
        if (labels[labels.length - 1].chars().allMatch(Character::isDigit)) {
            throw invalid("도메인 형식이 올바르지 않습니다.");
        }
        for (String suffix : RESERVED_SUFFIXES) {
            if (ascii.equals(suffix) || ascii.endsWith("." + suffix)) {
                throw invalid("내부용이나 예약된 도메인(." + suffix + ")은 등록할 수 없습니다.");
            }
        }
        return ascii;
    }

    private static String hostFromAuthority(String authority) {
        if (authority == null || authority.isEmpty()) {
            throw invalid("도메인을 입력해주세요.");
        }
        if (authority.contains("@")) {
            throw invalid("주소에 아이디나 비밀번호(@)를 넣을 수 없습니다.");
        }
        int colon = authority.lastIndexOf(':');
        // 포트가 붙어 있어도 URI가 호스트를 못 읽었으면 포트도 못 읽은 것이다. 이런 주소는 포트 없이만 받는다
        if (colon >= 0) {
            throw invalid("도메인 형식이 올바르지 않습니다.");
        }
        return authority;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_TARGET_URL, message);
    }
}
