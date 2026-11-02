package com.scanmesh.common.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;

/**
 * 서비스가 사용자 대신 요청을 보내도 되는 주소인지 판단한다 (SSRF 방어).
 * 내부망, 루프백, 클라우드 메타데이터(169.254.169.254) 같은 주소로 요청을 보내면
 * 사용자가 우리 서버를 통해 바깥에서 보이지 않는 곳을 들여다볼 수 있다.
 * API는 소유 인증 요청 전에, 워커는 점검 시작 전에 같은 규칙으로 검사한다.
 */
public final class PublicAddressPolicy {

    private static final List<Cidr> BLOCKED = Stream.of(
        // IPv4
        "0.0.0.0/8",          // 이 네트워크
        "10.0.0.0/8",         // 사설망
        "100.64.0.0/10",      // 통신사 NAT
        "127.0.0.0/8",        // 루프백
        "169.254.0.0/16",     // 링크 로컬, 클라우드 메타데이터
        "172.16.0.0/12",      // 사설망
        "192.0.0.0/24",       // IETF 프로토콜 할당
        "192.0.2.0/24",       // 문서용
        "192.88.99.0/24",     // 6to4 릴레이
        "192.168.0.0/16",     // 사설망
        "198.18.0.0/15",      // 성능 측정용
        "198.51.100.0/24",    // 문서용
        "203.0.113.0/24",     // 문서용
        "224.0.0.0/4",        // 멀티캐스트
        "240.0.0.0/4",        // 예약, 브로드캐스트
        // IPv6
        "::/96",              // 미지정, 루프백, IPv4 호환 주소
        "64:ff9b::/96",       // NAT64: 뒤에 붙은 IPv4가 내부망일 수 있다
        "64:ff9b:1::/48",     // 로컬 NAT64
        "100::/64",           // 버림용
        "2001::/23",          // IETF 프로토콜 할당 (Teredo 포함)
        "2001:db8::/32",      // 문서용
        "2002::/16",          // 6to4: 뒤에 붙은 IPv4가 내부망일 수 있다
        "fc00::/7",           // 사설망 (ULA)
        "fe80::/10",          // 링크 로컬
        "fec0::/10",          // 옛 사이트 로컬
        "ff00::/8"            // 멀티캐스트
    ).map(Cidr::parse).toList();

    private PublicAddressPolicy() {
    }

    public static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (isIpv4Mapped(bytes)) {
            // ::ffff:a.b.c.d 는 결국 IPv4 a.b.c.d로 연결되므로 IPv4 규칙으로 검사한다
            return isPublic(toIpv4(bytes));
        }
        return BLOCKED.stream().noneMatch(cidr -> cidr.contains(bytes));
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
    }

    private static InetAddress toIpv4(byte[] mapped) {
        try {
            return Inet4Address.getByAddress(new byte[]{mapped[12], mapped[13], mapped[14], mapped[15]});
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String cidr) {
            String[] parts = cidr.split("/");
            try {
                // 숫자 주소만 넣으므로 DNS 조회가 일어나지 않는다
                return new Cidr(InetAddress.getByName(parts[0]).getAddress(), Integer.parseInt(parts[1]));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(cidr, e);
            }
        }

        boolean contains(byte[] bytes) {
            byte[] net = network;
            if (bytes.length != net.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (bytes[i] != net[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (bytes[fullBytes] & mask) == (net[fullBytes] & mask);
        }
    }
}
