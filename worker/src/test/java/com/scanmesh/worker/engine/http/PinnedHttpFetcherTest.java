package com.scanmesh.worker.engine.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedHttpFetcherTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("host=" + exchange.getRequestHeaders().getFirst("Host")).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Set-Cookie", "a=1");
            exchange.getResponseHeaders().add("Set-Cookie", "b=2");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void DNS를_조회하지_않고_고정한_IP로_연결하면서_호스트_이름은_그대로_보낸다() throws IOException {
        try (TargetFetcher fetcher = new PinnedHttpFetcher("site.example.com", "127.0.0.1")) {
            TargetFetcher.Response response = fetcher.get(URI.create("http://site.example.com:" + port + "/"));

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("host=site.example.com:" + port);
            assertThat(response.headerValues("set-cookie")).containsExactly("a=1", "b=2");
        }
    }

    @Test
    void 점검_대상이_아닌_호스트로는_연결하지_않는다() throws IOException {
        try (TargetFetcher fetcher = new PinnedHttpFetcher("site.example.com", "127.0.0.1")) {
            assertThatThrownBy(() -> fetcher.get(URI.create("http://internal.example.com:" + port + "/")))
                .isInstanceOf(UnknownHostException.class);
        }
    }
}
