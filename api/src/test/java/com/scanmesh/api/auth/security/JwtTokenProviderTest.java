package com.scanmesh.api.auth.security;

import com.scanmesh.api.member.entity.Member;
import com.scanmesh.api.member.entity.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    void 발급한_토큰에서_회원_ID와_역할을_꺼낸다() {
        JwtTokenProvider provider = providerAt(NOW);
        String token = provider.createAccessToken(member(42L));

        assertThat(provider.parse(token)).contains(new MemberPrincipal(42L, MemberRole.USER));
    }

    @Test
    void 만료된_토큰은_거부한다() {
        String token = providerAt(NOW).createAccessToken(member(1L));

        assertThat(providerAt(NOW.plus(Duration.ofMinutes(29))).parse(token)).isPresent();
        assertThat(providerAt(NOW.plus(Duration.ofMinutes(31))).parse(token)).isEmpty();
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        String token = providerAt(NOW).createAccessToken(member(1L));
        JwtTokenProvider other = new JwtTokenProvider(
            "another-secret-key-that-is-at-least-32-bytes", Duration.ofMinutes(30), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(other.parse(token)).isEmpty();
        assertThat(other.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void 짧은_키로는_서버가_뜨지_않는다() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", Duration.ofMinutes(30), Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class);
    }

    private static JwtTokenProvider providerAt(Instant now) {
        return new JwtTokenProvider(SECRET, Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Member member(Long id) {
        Member member = Member.createUser("user@example.com", "encoded", "테스터");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
