package com.scanmesh.api.common.config;

import com.scanmesh.api.common.net.DnsTxtLookup;
import com.scanmesh.api.common.net.HostResolver;
import com.scanmesh.api.common.net.JndiDnsTxtLookup;
import com.scanmesh.api.common.net.SafeHttpClient;
import com.scanmesh.api.common.net.WebFetcher;
import com.scanmesh.common.net.PublicAddressPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** 사용자가 입력한 도메인으로 서버가 직접 DNS 조회나 HTTP 요청을 보낼 때 쓰는 빈 */
@Configuration
public class NetworkConfig {

    @Bean
    public HostResolver hostResolver() {
        return HostResolver.system();
    }

    @Bean
    public DnsTxtLookup dnsTxtLookup() {
        return new JndiDnsTxtLookup();
    }

    /** 소유 인증 파일은 토큰 한 줄이면 되므로 앞 4KB만 읽는다 */
    @Bean
    public WebFetcher webFetcher(HostResolver hostResolver) {
        return new SafeHttpClient(hostResolver, PublicAddressPolicy::isPublic, Duration.ofSeconds(5), 4096);
    }
}
