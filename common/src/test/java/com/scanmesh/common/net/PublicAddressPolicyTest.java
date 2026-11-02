package com.scanmesh.common.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Inet6Address;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAddressPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1", "127.255.255.254", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1",
        "169.254.169.254", "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1",
        "::", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "2001:db8::1", "64:ff9b::a00:1",
        "::ffff:127.0.0.1", "::ffff:169.254.169.254", "::127.0.0.1"
    })
    void 내부망과_예약된_주소는_막는다(String address) throws Exception {
        assertThat(PublicAddressPolicy.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "8.8.8.8", "1.1.1.1", "93.184.215.14", "172.32.0.1", "172.15.255.255", "100.128.0.1",
        "2606:4700:4700::1111", "2404:6800:4004:80a::200e"
    })
    void 공인_주소는_허용한다(String address) throws Exception {
        assertThat(PublicAddressPolicy.isPublic(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void 자바가_IPv4로_바꾸지_않은_IPv4_매핑_주소도_IPv4_규칙으로_검사한다() throws Exception {
        byte[] mappedLoopback = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 127, 0, 0, 1};
        InetAddress address = Inet6Address.getByAddress(null, mappedLoopback, -1);

        assertThat(address).isInstanceOf(Inet6Address.class);
        assertThat(PublicAddressPolicy.isPublic(address)).isFalse();
    }
}
