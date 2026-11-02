package com.scanmesh.api.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 도메인이 내부망이나 예약된 주소를 가리켜서 연결하지 않았다.
 * HTTP 클라이언트의 DNS 조회 단계에서 던지므로, 그 단계가 던질 수 있는 UnknownHostException을 상속한다.
 */
public class BlockedAddressException extends UnknownHostException {

    public BlockedAddressException(String host, InetAddress address) {
        super(host + " -> " + address.getHostAddress() + " 는 허용되지 않는 주소입니다.");
    }
}
