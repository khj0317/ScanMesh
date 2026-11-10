package com.scanmesh.worker.engine.http;

import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/** 점검 대상 사이트로 GET 요청을 보낸다. 리다이렉트는 따라가지 않고 응답 그대로 돌려준다 */
public interface TargetFetcher extends AutoCloseable {

    Response get(URI uri) throws IOException;

    @Override
    void close() throws IOException;

    /**
     * @param headers     헤더 이름은 소문자
     * @param certificate https면 서버 인증서, 아니면 null
     */
    record Response(int status, Map<String, List<String>> headers, String body, X509Certificate certificate) {

        public List<String> headerValues(String name) {
            return headers.getOrDefault(name, List.of());
        }

        public String firstHeader(String name) {
            List<String> values = headerValues(name);
            return values.isEmpty() ? null : values.getFirst();
        }

        public boolean hasHeader(String name) {
            return !headerValues(name).isEmpty();
        }
    }
}
