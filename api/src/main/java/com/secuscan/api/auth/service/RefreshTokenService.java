package com.secuscan.api.auth.service;

import com.secuscan.api.auth.entity.RefreshToken;
import com.secuscan.api.auth.repository.RefreshTokenRepository;
import com.secuscan.api.common.exception.BusinessException;
import com.secuscan.api.common.exception.ErrorCode;
import com.secuscan.api.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

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
    private final SecureRandom random = new SecureRandom();

    @Value("${jwt.refresh-token-validity}")
    private Duration refreshTokenValidity;

    public record Rotated(Member member, String refreshToken) {
    }

    @Transactional
    public String issue(Member member) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(member, hash(raw), now, now.plus(refreshTokenValidity)));
        return raw;
    }

    // 재사용을 감지해 모든 토큰을 끊은 결과는 예외와 함께 롤백되지 않고 남아야 한다
    @Transactional(noRollbackFor = BusinessException.class)
    public Rotated rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
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
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
            .ifPresent(token -> token.revoke(clock.instant()));
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
