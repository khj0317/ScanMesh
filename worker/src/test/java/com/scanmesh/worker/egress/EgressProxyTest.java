package com.scanmesh.worker.egress;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EgressProxyTest {

    private HttpServer target;
    private EgressProxy proxy;
    private int targetPort;

    @BeforeEach
    void start() throws IOException {
        target = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        target.createContext("/", exchange -> {
            byte[] body = ("path=" + exchange.getRequestURI() + " host=" + exchange.getRequestHeaders().getFirst("Host")).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        target.start();
        targetPort = target.getAddress().getPort();
        // 대상 도메인 site.example.com 을 검사해서 고정한 IP가 127.0.0.1 이라고 본다
        proxy = new EgressProxy(0, "site.example.com", targetPort, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(proxy::serve);
    }

    @AfterEach
    void stop() throws IOException {
        proxy.close();
        target.stop(0);
    }

    @Test
    void 대상_호스트로_가는_http_요청은_고정한_IP로_넘긴다() throws IOException {
        String response = send("GET http://site.example.com:" + targetPort + "/a?b=1 HTTP/1.1\r\nHost: site.example.com:" + targetPort + "\r\nProxy-Connection: keep-alive\r\n\r\n");

        assertThat(response).startsWith("HTTP/1.1 200").contains("path=/a?b=1", "host=site.example.com:" + targetPort);
    }

    @Test
    void 대상_호스트로_가는_CONNECT는_터널을_연다() throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), proxy.port())) {
            OutputStream out = socket.getOutputStream();
            out.write(("CONNECT site.example.com:" + targetPort + " HTTP/1.1\r\nHost: site.example.com\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            assertThat(readUntilBlankLine(in)).startsWith("HTTP/1.1 200 Connection Established");

            // 터널 안에서는 대상 서버와 바로 이야기한다
            out.write(("GET /tunnel HTTP/1.1\r\nHost: site.example.com\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("path=/tunnel");
        }
    }

    @Test
    void 다른_호스트나_내부_주소로_가는_요청은_막는다() throws IOException {
        assertThat(send("GET http://169.254.169.254/latest/meta-data HTTP/1.1\r\nHost: 169.254.169.254\r\n\r\n")).startsWith("HTTP/1.1 403");
        assertThat(send("GET http://host.docker.internal:" + targetPort + "/ HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
        assertThat(send("CONNECT 192.168.0.1:443 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
        assertThat(send("CONNECT site.example.com.attacker.test:443 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
    }

    @Test
    void 대상_호스트라도_허용하지_않은_포트는_막는다() throws IOException {
        assertThat(send("CONNECT site.example.com:22 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
        assertThat(send("GET http://site.example.com:6379/ HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
    }

    @Test
    void 프록시_형식이_아닌_요청은_넘기지_않는다() throws IOException {
        assertThat(send("GET / HTTP/1.1\r\nHost: site.example.com\r\n\r\n")).startsWith("HTTP/1.1 400");
        assertThat(send("GET file:///etc/passwd HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 400");
    }

    private String send(String request) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), proxy.port())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readUntilBlankLine(InputStream in) throws IOException {
        StringBuilder text = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            text.append((char) b);
            if (text.toString().endsWith("\r\n\r\n")) {
                break;
            }
        }
        return text.toString();
    }
}
