package com.scanmesh.api.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 리프레시 토큰을 담는 쿠키. 화면의 JavaScript가 읽을 수 없게(HttpOnly) 해서, XSS가 생겨도 오래 쓰는 토큰은 빼 가지 못하게 한다.
 * <ul>
 *   <li>SameSite=Strict: 다른 사이트에서 보낸 요청에는 실리지 않아 CSRF로 토큰을 돌릴 수 없다.
 *       개발 환경의 localhost:5181 → localhost:8190은 포트만 달라 같은 사이트로 본다.
 *       배포에서는 화면 서버가 /api를 API 서버로 넘겨 같은 출처로 만든다.</li>
 *   <li>Path=/api/auth: 로그인 유지에 필요한 요청에만 실린다.</li>
 *   <li>Secure: 배포(https)에서만 켠다. 개발 환경의 http에서 켜면 브라우저가 쿠키를 저장하지 않는다.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "scanmesh_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookie(
        @Value("${app.auth.cookie-secure}") boolean secure,
        @Value("${jwt.refresh-token-validity}") Duration maxAge
    ) {
        this.secure = secure;
        this.maxAge = maxAge;
    }

    public ResponseCookie issue(String refreshToken) {
        return builder(refreshToken).maxAge(maxAge).build();
    }

    /** 브라우저에 남은 쿠키를 지운다 */
    public ResponseCookie clear() {
        return builder("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(PATH);
    }
}
