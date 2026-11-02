package com.scanmesh.api.target;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 개발할 때 연습용 대상 앱(Juice Shop 등)을 점검하려고 SSRF 방어와 소유 인증을 건너뛰는 호스트 목록.
 * 목록에 있는 호스트는 내부 주소여도 등록되고, 등록하자마자 인증된 것으로 본다.
 * 운영 서버가 내부망을 점검하는 일이 없도록 배포(prod 프로필)에서 값이 있으면 서버가 뜨지 않는다.
 */
@Slf4j
@Component
public class DevAllowedHosts {

    private final Set<String> hosts;

    public DevAllowedHosts(@Value("${app.targets.dev-allowed-hosts:}") List<String> configured, Environment environment) {
        this.hosts = configured.stream()
            .map(host -> host.strip().toLowerCase(Locale.ROOT))
            .filter(host -> !host.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        if (hosts.isEmpty()) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("배포(prod)에서는 app.targets.dev-allowed-hosts(DEV_ALLOWED_HOSTS)를 쓸 수 없습니다: " + hosts);
        }
        log.warn("개발용 허용 호스트 {}: SSRF 방어와 소유 인증을 건너뜁니다. 배포에서는 쓰지 마세요.", hosts);
    }

    public Set<String> hosts() {
        return hosts;
    }

    public boolean contains(String host) {
        return hosts.contains(host);
    }
}
