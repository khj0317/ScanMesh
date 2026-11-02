package com.scanmesh.api.auth.service;

import com.scanmesh.api.auth.entity.RefreshToken;
import com.scanmesh.api.auth.repository.RefreshTokenRepository;
import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.common.security.SecretTokens;
import com.scanmesh.api.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 리프레시 토큰은 쓸 때마다 새것으로 바꾼다(회전). 이미 바꿔서 버린 토큰이 다시 들어오면
 * 누군가 토큰을 훔쳐 쓴 것으로 보고 그 회원의 로그인을 모두 끊는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Value("${jwt.refresh-token-validity}")
    private Duration refreshTokenValidity;

    public record Rotated(Member member, String refreshToken) {
    }

    @Transactional
    public String issue(Member member) {
        String raw = SecretTokens.generate();
        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(member, SecretTokens.hash(raw), now, now.plus(refreshTokenValidity)));
        return raw;
    }

    // 재사용을 감지해 모든 토큰을 끊은 결과는 예외와 함께 롤백되지 않고 남아야 한다
    @Transactional(noRollbackFor = BusinessException.class)
    public Rotated rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(SecretTokens.hash(rawToken))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllByMemberId(token.getMember().getId(), now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        token.revoke(now);
        return new Rotated(token.getMember(), issue(token.getMember()));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHashForUpdate(SecretTokens.hash(rawToken))
            .ifPresent(token -> token.revoke(clock.instant()));
    }
}
