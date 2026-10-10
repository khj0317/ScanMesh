package com.scanmesh.api.auth.service;

import com.scanmesh.api.auth.dto.LoginRequest;
import com.scanmesh.api.auth.dto.SignupRequest;
import com.scanmesh.api.auth.dto.TokenResponse;
import com.scanmesh.api.auth.security.JwtTokenProvider;
import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.member.dto.MemberResponse;
import com.scanmesh.api.member.entity.Member;
import com.scanmesh.api.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    /**
     * 없는 이메일로 로그인해도 비밀번호 비교를 한 번 하게 만드는 값.
     * 바로 실패시키면 응답이 눈에 띄게 빨라져서, 응답 시간으로 가입된 이메일을 알아낼 수 있다.
     */
    private final String dummyPasswordHash;

    public AuthService(
        MemberRepository memberRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        RefreshTokenService refreshTokenService
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        Member member = Member.createUser(email, passwordEncoder.encode(request.password()), request.name().strip());
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<Member> found = memberRepository.findByEmail(normalizeEmail(request.email()));
        String hash = found.map(Member::getPassword).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        Member member = found.get();
        return tokens(member, refreshTokenService.issue(member));
    }

    // 재사용 감지로 모든 토큰을 끊은 결과가 이 트랜잭션의 롤백에 휩쓸려 사라지지 않게 한다
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        return tokens(rotated.member(), rotated.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private TokenResponse tokens(Member member, String refreshToken) {
        return new TokenResponse(
            jwtTokenProvider.createAccessToken(member),
            refreshToken,
            "Bearer",
            jwtTokenProvider.getAccessTokenValidity().toSeconds()
        );
    }

    /** 대소문자나 앞뒤 공백만 다른 이메일로 중복 가입하지 못하게 한다 */
    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
