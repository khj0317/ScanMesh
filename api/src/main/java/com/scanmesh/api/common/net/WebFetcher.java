package com.scanmesh.api.common.net;

import java.io.IOException;
import java.net.URI;

/** 사용자가 입력한 주소로 GET 요청을 보낸다. 테스트에서 실제 요청 대신 정해 둔 응답을 돌려주도록 바꿔 끼운다 */
public interface WebFetcher {

    /**
     * @throws BlockedAddressException 내부망이나 예약된 주소를 가리킬 때
     * @throws RedirectNotAllowedException 다른 호스트로 리다이렉트하거나 리다이렉트가 너무 많을 때
     * @throws IOException 연결 실패, 시간 초과
     */
    FetchResult get(URI uri) throws IOException;

    record FetchResult(int status, String body) {
    }
}
