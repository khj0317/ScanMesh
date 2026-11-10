package com.scanmesh.worker.engine.http;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** 점검 대상마다 그 호스트로만 연결되는 클라이언트를 만든다. 테스트에서 실제 요청 대신 바꿔 끼운다 */
@Component
public class TargetFetcherFactory {

    private static final String DOCKER_HOST = "host.docker.internal";

    /** @param pinnedAddress 연결할 IP. 개발용 허용 호스트면 null이고 시스템 DNS를 쓴다 */
    public TargetFetcher create(String host, String pinnedAddress) {
        return new PinnedHttpFetcher(host, pinnedAddress != null ? pinnedAddress : dockerHostAddress(host));
    }

    /**
     * host.docker.internal은 컨테이너 안에서만 풀리는 경우가 많다. 워커가 내 PC에서 직접 돌 때 이 이름은 내 PC 자신을 뜻하므로
     * 엔진 컨테이너와 같은 대상을 보도록 루프백으로 연결한다. 고정 IP가 없는 개발용 허용 호스트에만 해당한다.
     */
    private static String dockerHostAddress(String host) {
        if (!DOCKER_HOST.equals(host)) {
            return null;
        }
        try {
            InetAddress.getByName(host);
            return null;
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }
}
