package com.secuscan.api.auth.dto;

/** expiresIn: 액세스 토큰 유효 시간(초) */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
