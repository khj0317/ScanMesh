package com.scanmesh.api.common.net;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 사용자가 입력한 주소로 서버가 직접 요청을 보낼 때 쓰는 HTTP 클라이언트 (SSRF 방어).
 * <ul>
 *   <li>DNS로 찾은 주소가 하나라도 허용되지 않으면 연결하지 않는다.</li>
 *   <li>검사를 통과한 주소로 그대로 연결한다. 검사할 때와 연결할 때 DNS를 따로 조회하면, 그 사이에 응답을
 *       내부 주소로 바꾸는 공격(DNS 리바인딩)에 뚫린다.</li>
 *   <li>리다이렉트는 같은 호스트 안에서만 따라가고, 따라갈 때마다 주소를 다시 검사한다.</li>
 *   <li>전체 시간과 읽는 본문 크기를 제한해서, 느리거나 큰 응답으로 서버 자원을 붙잡지 못하게 한다.</li>
 * </ul>
 */
public class SafeHttpClient implements WebFetcher, AutoCloseable {

    private static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final CloseableHttpClient client;
    private final Duration totalTimeout;
    private final int maxBodyBytes;

    public SafeHttpClient(HostResolver resolver, Predicate<InetAddress> addressAllowed, Duration timeout, int maxBodyBytes) {
        this.totalTimeout = timeout;
        this.maxBodyBytes = maxBodyBytes;
        Timeout perStep = Timeout.of(timeout);
        this.client = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new CheckingDnsResolver(resolver, addressAllowed))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(perStep)
                    .setSocketTimeout(perStep)
                    .build())
                .build())
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(perStep)
                .setResponseTimeout(perStep)
                .build())
            // 시스템 프록시 설정은 읽지 않는다(useSystemProperties를 부르지 않음). 프록시를 거치면 검사한 주소로 연결되지 않는다
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .setUserAgent("ScanMesh-Verifier/1.0")
            .build();
    }

    @Override
    public FetchResult get(URI uri) throws IOException {
        URI current = uri;
        for (int redirects = 0; ; redirects++) {
            Response response = getOnce(current);
            if (response.location() == null) {
                return new FetchResult(response.status(), response.body());
            }
            URI next = followable(current, response.location());
            if (next == null || redirects >= MAX_REDIRECTS) {
                throw new RedirectNotAllowedException(current, response.location());
            }
            current = next;
        }
    }

    private Response getOnce(URI uri) throws IOException {
        HttpGet request = new HttpGet(uri);
        // 단계별 제한과 별개로 전체 시간도 묶는다. 한 바이트씩 천천히 보내는 응답을 끊기 위해서다
        CompletableFuture.delayedExecutor(totalTimeout.toMillis(), TimeUnit.MILLISECONDS).execute(request::cancel);
        // 본문을 끝까지 읽지 않고 닫으면 연결을 재사용하지 않고 버린다
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            int status = response.getCode();
            if (REDIRECT_STATUSES.contains(status)) {
                Header location = response.getFirstHeader("Location");
                if (location != null) {
                    return new Response(status, "", location.getValue());
                }
            }
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : readLimited(entity.getContent());
            return new Response(status, body, null);
        }
    }

    private String readLimited(InputStream in) throws IOException {
        try (in) {
            return new String(in.readNBytes(maxBodyBytes), StandardCharsets.UTF_8);
        }
    }

    /** 같은 호스트의 http(s) 주소면 따라갈 주소를, 아니면 null을 돌려준다 */
    private static URI followable(URI from, String location) {
        try {
            URI next = from.resolve(location.strip());
            String scheme = next.getScheme() == null ? "" : next.getScheme().toLowerCase(Locale.ROOT);
            boolean httpScheme = scheme.equals("http") || scheme.equals("https");
            boolean sameHost = next.getHost() != null && next.getHost().equalsIgnoreCase(from.getHost());
            return httpScheme && sameHost && next.getRawUserInfo() == null ? next : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private record Response(int status, String body, String location) {
    }

    private record CheckingDnsResolver(HostResolver resolver, Predicate<InetAddress> addressAllowed) implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = resolver.resolve(host);
            for (InetAddress address : addresses) {
                if (!addressAllowed.test(address)) {
                    throw new BlockedAddressException(host, address);
                }
            }
            return addresses;
        }

        // 라이브러리가 실제로 부르는 메서드. 기본 구현이 위 검사를 거치지 않게 바뀌어도 검사가 빠지지 않도록 직접 구현한다
        @Override
        public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
            return Arrays.stream(resolve(host))
                .map(address -> new InetSocketAddress(address, port))
                .toList();
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }
}
