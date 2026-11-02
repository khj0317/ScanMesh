package com.scanmesh.api.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** 도메인을 IP 주소로 바꾼다. 테스트에서 실제 DNS 대신 정해 둔 주소를 돌려주도록 바꿔 끼운다 */
@FunctionalInterface
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;

    static HostResolver system() {
        return InetAddress::getAllByName;
    }
}
