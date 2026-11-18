package com.scanmesh.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * expiresIn: 액세스 토큰 유효 시간(초)
 *
 * @param refreshToken 응답 본문에는 싣지 않고 HttpOnly 쿠키로만 보낸다(RefreshTokenCookie)
 */
public record TokenResponse(String accessToken, @JsonIgnore String refreshToken, String tokenType, long expiresIn) {
}
