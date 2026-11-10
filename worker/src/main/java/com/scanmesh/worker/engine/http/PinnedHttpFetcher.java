package com.scanmesh.worker.engine.http;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 점검 대상 호스트로만 연결되는 HTTP 클라이언트 (SSRF 방어).
 * 워커는 내 PC의 내부망 안에서 돌기 때문에, 응답이 어디로 보내든 정해진 호스트와 검사를 통과한 IP 외에는 연결하지 않는다.
 */
class PinnedHttpFetcher implements TargetFetcher {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Timeout TIMEOUT = Timeout.ofSeconds(10);

    private final CloseableHttpClient client;

    PinnedHttpFetcher(String host, String pinnedAddress) {
        this.client = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new SingleHostResolver(host, pinnedAddress))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(TIMEOUT)
                    .setSocketTimeout(TIMEOUT)
                    .build())
                .build())
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(TIMEOUT)
                .setResponseTimeout(TIMEOUT)
                .build())
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .setUserAgent("ScanMesh-Scanner/1.0")
            .build();
    }

    @Override
    public Response get(URI uri) throws IOException {
        HttpClientContext context = HttpClientContext.create();
        // 본문을 끝까지 읽지 않고 닫으면 연결을 재사용하지 않고 버린다
        try (ClassicHttpResponse response = client.executeOpen(null, new HttpGet(uri), context)) {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (Header header : response.getHeaders()) {
                headers.computeIfAbsent(header.getName().toLowerCase(Locale.ROOT), name -> new ArrayList<>()).add(header.getValue());
            }
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : read(entity.getContent());
            return new Response(response.getCode(), headers, body, certificate(context.getSSLSession()));
        }
    }

    private static String read(InputStream in) throws IOException {
        try (in) {
            return new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
        }
    }

    private static X509Certificate certificate(SSLSession session) {
        if (session == null) {
            return null;
        }
        try {
            Certificate[] chain = session.getPeerCertificates();
            return chain.length > 0 && chain[0] instanceof X509Certificate x509 ? x509 : null;
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private record SingleHostResolver(String host, String pinnedAddress) implements DnsResolver {

        @Override
        public InetAddress[] resolve(String requested) throws UnknownHostException {
            if (!host.equalsIgnoreCase(requested)) {
                throw new UnknownHostException(requested + " 는 점검 대상이 아닙니다.");
            }
            // 숫자 주소를 넘기면 DNS 조회가 일어나지 않는다
            return InetAddress.getAllByName(pinnedAddress == null ? host : pinnedAddress);
        }

        @Override
        public List<InetSocketAddress> resolve(String requested, int port) throws UnknownHostException {
            return Arrays.stream(resolve(requested)).map(address -> new InetSocketAddress(address, port)).toList();
        }

        @Override
        public String resolveCanonicalHostname(String requested) {
            return requested;
        }
    }
}
