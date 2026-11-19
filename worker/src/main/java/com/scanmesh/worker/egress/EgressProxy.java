package com.scanmesh.worker.egress;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 점검 엔진의 유일한 출구. 엔진 컨테이너는 바깥으로 나갈 수 없는 Docker 내부 네트워크에 두고, 이 프록시만 그 네트워크와 바깥에 함께 붙인다.
 * 프록시는 점검 대상 호스트(대상 포트, 80, 443)로 가는 요청만 넘기고, 연결은 워커가 검사해 고정한 IP로만 한다.
 * 그래서 대상 사이트가 응답으로 내부망·클라우드 메타데이터 주소를 가리켜도 엔진은 그곳에 닿지 못한다 (SSRF 방어).
 *
 * <p>워커가 이 클래스 파일 하나로 프록시 이미지를 만들어 컨테이너로 띄운다(EgressSandbox). 그래서 스프링이나 다른 라이브러리에
 * 기대지 않고, 컴파일하면 클래스 파일이 하나만 나오도록 중첩 클래스를 쓰지 않는다.
 */
public final class EgressProxy {

    /** 이 줄이 찍히면 요청을 받을 준비가 된 것이다. 워커가 컨테이너 로그에서 기다린다 */
    public static final String READY = "EGRESS_PROXY_READY";
    public static final int PORT = 3128;

    private static final int MAX_LINE = 8192;
    private static final int MAX_HEADERS = 100;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int IDLE_TIMEOUT_MS = 120_000;

    private final String allowedHost;
    private final Set<Integer> allowedPorts;
    private final InetAddress pinnedAddress;
    private final ServerSocket server;

    /**
     * @param pinnedAddress 연결할 IP. null이면 프록시가 호스트 이름을 직접 찾는다 (개발용 허용 호스트)
     */
    public EgressProxy(int port, String allowedHost, int targetPort, InetAddress pinnedAddress) throws IOException {
        this.allowedHost = allowedHost.toLowerCase(Locale.ROOT);
        // http 대상이 https로 리다이렉트하는 일이 흔해서 기본 포트 둘은 함께 허용한다. 호스트는 그대로 하나다
        this.allowedPorts = Set.of(targetPort, 80, 443);
        this.pinnedAddress = pinnedAddress;
        this.server = new ServerSocket(port);
    }

    /** 사용법: EgressProxy {대상 호스트} {대상 포트} {고정 IP 또는 -} */
    public static void main(String[] args) throws IOException {
        InetAddress pinned = args[2].equals("-") ? null : InetAddress.getByName(args[2]);
        EgressProxy proxy = new EgressProxy(PORT, args[0], Integer.parseInt(args[1]), pinned);
        System.out.println(READY + " host=" + args[0] + " port=" + args[1] + " pinned=" + args[2]);
        System.out.flush();
        proxy.serve();
    }

    public int port() {
        return server.getLocalPort();
    }

    public void serve() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                Thread.ofVirtual().start(() -> handle(client));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    log("accept 실패: " + e);
                }
            }
        }
    }

    public void close() throws IOException {
        server.close();
    }

    private void handle(Socket client) {
        try (client) {
            client.setSoTimeout(IDLE_TIMEOUT_MS);
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }
            List<String> headers = readHeaders(in);
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                reply(out, "400 Bad Request");
                return;
            }
            if (parts[0].equalsIgnoreCase("CONNECT")) {
                tunnel(parts[1], in, out);
            } else {
                forward(parts[0], parts[1], parts[2], headers, in, out);
            }
        } catch (IOException e) {
            // 엔진이 연결을 끊거나 시간이 지나 닫힌 경우. 다른 요청에는 영향이 없다
        }
    }

    /** https: CONNECT host:port 를 받아 그대로 이어 준다. TLS는 엔진과 대상 사이에서 그대로 오간다 */
    private void tunnel(String authority, InputStream in, OutputStream out) throws IOException {
        int colon = authority.lastIndexOf(':');
        if (colon <= 0) {
            reply(out, "400 Bad Request");
            return;
        }
        String host = authority.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(authority.substring(colon + 1));
        } catch (NumberFormatException e) {
            reply(out, "400 Bad Request");
            return;
        }
        if (!allowed(host, port)) {
            deny(out, host, port);
            return;
        }
        try (Socket upstream = connect(host, port)) {
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            pipe(in, out, upstream);
        }
    }

    /** http: GET http://host/path 를 받아 GET /path 로 바꿔 보낸다 */
    private void forward(String method, String target, String version, List<String> headers, InputStream in, OutputStream out)
        throws IOException {
        URI uri;
        try {
            uri = new URI(target);
        } catch (URISyntaxException e) {
            reply(out, "400 Bad Request");
            return;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            reply(out, "400 Bad Request");
            return;
        }
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        if (!allowed(uri.getHost(), port)) {
            deny(out, uri.getHost(), port);
            return;
        }
        try (Socket upstream = connect(uri.getHost(), port)) {
            StringBuilder head = new StringBuilder();
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            head.append(method).append(' ').append(path);
            if (uri.getRawQuery() != null) {
                head.append('?').append(uri.getRawQuery());
            }
            head.append(' ').append(version).append("\r\n");
            for (String header : headers) {
                String name = header.split(":", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (!name.startsWith("proxy-") && !name.equals("connection")) {
                    head.append(header).append("\r\n");
                }
            }
            // 한 연결로 다른 호스트에 요청을 이어 보내지 못하게, 요청 하나가 끝나면 연결을 닫게 한다
            head.append("Connection: close\r\n\r\n");
            upstream.getOutputStream().write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            pipe(in, out, upstream);
        }
    }

    boolean allowed(String host, int port) {
        return allowedHost.equals(host.toLowerCase(Locale.ROOT)) && allowedPorts.contains(port);
    }

    /** 고정 IP가 있으면 이름과 상관없이 그 IP로만 연결한다. 엔진이 보낸 호스트 이름으로 DNS를 다시 찾지 않는다 */
    private Socket connect(String host, int port) throws IOException {
        InetAddress address = pinnedAddress != null ? pinnedAddress : InetAddress.getByName(host);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(IDLE_TIMEOUT_MS);
        return socket;
    }

    private static void pipe(InputStream clientIn, OutputStream clientOut, Socket upstream) throws IOException {
        InputStream upstreamIn = upstream.getInputStream();
        OutputStream upstreamOut = upstream.getOutputStream();
        Thread toUpstream = Thread.ofVirtual().start(() -> {
            try {
                clientIn.transferTo(upstreamOut);
                upstream.shutdownOutput();
            } catch (IOException e) {
                // 한쪽이 먼저 닫히면 나머지도 곧 끝난다
            }
        });
        try {
            upstreamIn.transferTo(clientOut);
            clientOut.flush();
        } finally {
            upstream.close();
            toUpstream.interrupt();
        }
    }

    private static void deny(OutputStream out, String host, int port) throws IOException {
        log("DENY " + host + ":" + port);
        reply(out, "403 Forbidden");
    }

    private static void reply(OutputStream out, String status) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static List<String> readHeaders(InputStream in) throws IOException {
        List<String> headers = new ArrayList<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            if (headers.size() >= MAX_HEADERS) {
                throw new IOException("헤더가 너무 많습니다.");
            }
            headers.add(line);
        }
        return headers;
    }

    /** CRLF로 끝나는 한 줄. 너무 긴 줄은 거절한다 */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            if (line.size() >= MAX_LINE) {
                throw new IOException("요청 줄이 너무 깁니다.");
            }
            line.write(b);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }

    private static void log(String message) {
        System.err.println(message);
    }
}
