package com.scanmesh.api.common.net;

import com.scanmesh.common.net.PublicAddressPolicy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 내 PC(127.0.0.1)에 작은 HTTP 서버를 띄워 확인한다. 기본 정책이면 이 서버는 루프백이라 막혀야 하고,
 * 리다이렉트·본문 제한 같은 나머지 동작은 루프백을 허용한 정책으로 확인한다.
 */
class SafeHttpClientTest {

    private static final HostResolver LOCAL = host -> new InetAddress[]{InetAddress.getLoopbackAddress()};

    private HttpServer server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, "token-123"));
        server.createContext("/big", exchange -> respond(exchange, 200, "a".repeat(100_000)));
        server.createContext("/same-host", exchange -> redirect(exchange, "/ok"));
        server.createContext("/other-host", exchange -> redirect(exchange, "http://169.254.169.254/latest/meta-data"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.start();
        base = "http://site.example.com:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 도메인이_루프백을_가리키면_연결하지_않는다() throws IOException {
        try (SafeHttpClient client = new SafeHttpClient(LOCAL, PublicAddressPolicy::isPublic, Duration.ofSeconds(2), 1024)) {
            assertThatThrownBy(() -> client.get(URI.create(base + "/ok")))
                .isInstanceOf(BlockedAddressException.class);
        }
        assertThat(hits).hasValue(0);
    }

    @Test
    void IP_주소로_직접_요청해도_검사한다() throws IOException {
        try (SafeHttpClient client = new SafeHttpClient(HostResolver.system(), PublicAddressPolicy::isPublic, Duration.ofSeconds(2), 1024)) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ok";
            assertThatThrownBy(() -> client.get(URI.create(url)))
                .isInstanceOf(BlockedAddressException.class);
        }
        assertThat(hits).hasValue(0);
    }

    @Test
    void 같은_호스트_리다이렉트는_따라간다() throws IOException {
        try (SafeHttpClient client = allowLoopback()) {
            WebFetcher.FetchResult result = client.get(URI.create(base + "/same-host"));

            assertThat(result.status()).isEqualTo(200);
            assertThat(result.body()).isEqualTo("token-123");
        }
    }

    @Test
    void 다른_호스트로의_리다이렉트와_끝없는_리다이렉트는_따라가지_않는다() throws IOException {
        try (SafeHttpClient client = allowLoopback()) {
            assertThatThrownBy(() -> client.get(URI.create(base + "/other-host")))
                .isInstanceOf(RedirectNotAllowedException.class);
            assertThatThrownBy(() -> client.get(URI.create(base + "/loop")))
                .isInstanceOf(RedirectNotAllowedException.class);
        }
    }

    @Test
    void 본문은_정한_크기까지만_읽는다() throws IOException {
        try (SafeHttpClient client = allowLoopback()) {
            assertThat(client.get(URI.create(base + "/big")).body()).hasSize(1024);
        }
    }

    private SafeHttpClient allowLoopback() {
        return new SafeHttpClient(LOCAL, address -> address.isLoopbackAddress(), Duration.ofSeconds(2), 1024);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        hits.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException ignored) {
            // 클라이언트가 본문을 다 읽지 않고 끊으면 쓰기가 실패한다
        }
    }

    private void redirect(com.sun.net.httpserver.HttpExchange exchange, String location) throws IOException {
        hits.incrementAndGet();
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
