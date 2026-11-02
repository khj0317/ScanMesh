package com.scanmesh.api.target;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevAllowedHostsTest {

    @Test
    void 배포에서_개발용_허용_호스트를_설정하면_서버가_뜨지_않는다() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new DevAllowedHosts(List.of("localhost"), prod))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 배포에서도_비어_있으면_괜찮다() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        // 환경 변수가 없으면 빈 문자열 하나로 들어올 수 있다
        assertThat(new DevAllowedHosts(List.of(""), prod).hosts()).isEmpty();
        assertThat(new DevAllowedHosts(List.of(), prod).hosts()).isEmpty();
    }

    @Test
    void 호스트는_공백을_떼고_소문자로_맞춘다() {
        DevAllowedHosts hosts = new DevAllowedHosts(List.of(" Juice-Shop ", "localhost"), new MockEnvironment());

        assertThat(hosts.hosts()).containsExactlyInAnyOrder("juice-shop", "localhost");
    }
}
