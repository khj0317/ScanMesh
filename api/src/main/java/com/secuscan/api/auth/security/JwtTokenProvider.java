package com.secuscan.api.auth.security;

import com.secuscan.api.member.entity.Member;
import com.secuscan.api.member.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** 액세스 토큰 발급과 검증. subject에는 바뀔 수 있는 이메일 대신 회원 ID를 넣는다 */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenValidity;
    private final Clock clock;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-validity}") Duration accessTokenValidity,
        Clock clock
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256은 256비트 이상의 키가 필요하다. 짧은 키로 조용히 뜨지 않고 시작할 때 실패시킨다
            throw new IllegalStateException("jwt.secret은 32바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenValidity = accessTokenValidity;
        this.clock = clock;
    }

    public String createAccessToken(Member member) {
        Instant now = clock.instant();
        return Jwts.builder()
            .subject(String.valueOf(member.getId()))
            .claim(ROLE_CLAIM, member.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenValidity)))
            .signWith(key)
            .compact();
    }

    public Duration getAccessTokenValidity() {
        return accessTokenValidity;
    }

    /** 서명이 맞고 만료되지 않은 토큰이면 로그인 정보를, 아니면 빈 값을 돌려준다 */
    public Optional<MemberPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            if (claims.getSubject() == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new MemberPrincipal(Long.valueOf(claims.getSubject()), MemberRole.valueOf(role)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
