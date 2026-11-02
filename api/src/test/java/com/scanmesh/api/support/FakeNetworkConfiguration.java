package com.scanmesh.api.support;

import com.scanmesh.api.common.net.DnsTxtLookup;
import com.scanmesh.api.common.net.HostResolver;
import com.scanmesh.api.common.net.WebFetcher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트가 실제 DNS나 외부 사이트에 의존하지 않도록, 도메인 조회와 외부 요청을 정해 둔 값으로 바꾼다.
 * 컨텍스트를 모든 테스트 클래스가 공유하므로, 테스트마다 고유한 도메인을 써서 값이 겹치지 않게 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeNetworkConfiguration {

    /** 등록하지 않은 도메인은 공인 주소(93.184.215.14)로, "nxdomain"이 들어간 도메인은 없는 도메인으로 본다 */
    public static final Map<String, InetAddress[]> HOSTS = new ConcurrentHashMap<>();
    public static final Map<String, List<String>> TXT_RECORDS = new ConcurrentHashMap<>();
    /** 등록하지 않은 주소는 404로 응답한다. 값이 IOException이면 그 예외를 던진다 */
    public static final Map<String, Object> WEB = new ConcurrentHashMap<>();

    private static final InetAddress PUBLIC_ADDRESS = address("93.184.215.14");

    @Bean
    @Primary
    HostResolver fakeHostResolver() {
        return host -> {
            if (host.contains("nxdomain")) {
                throw new UnknownHostException(host);
            }
            return HOSTS.getOrDefault(host, new InetAddress[]{PUBLIC_ADDRESS});
        };
    }

    @Bean
    @Primary
    DnsTxtLookup fakeDnsTxtLookup() {
        return name -> TXT_RECORDS.getOrDefault(name, List.of());
    }

    @Bean
    @Primary
    WebFetcher fakeWebFetcher() {
        return (URI uri) -> {
            Object response = WEB.get(uri.toString());
            if (response instanceof IOException e) {
                throw e;
            }
            if (response instanceof WebFetcher.FetchResult result) {
                return result;
            }
            return new WebFetcher.FetchResult(404, "Not Found");
        };
    }

    public static InetAddress address(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(ip, e);
        }
    }
}
