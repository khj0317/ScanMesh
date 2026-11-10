package com.scanmesh.worker.task;

import com.scanmesh.common.net.PublicAddressPolicy;
import com.scanmesh.worker.WorkerProperties;
import com.scanmesh.worker.api.ClaimedTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 점검을 시작하기 직전에 대상 도메인이 내부망을 가리키지 않는지 다시 확인한다 (SSRF 방어).
 * 등록할 때 검사했어도 그 뒤에 DNS 값을 내부 주소로 바꿀 수 있고, 워커는 내 PC의 내부망 안에서 돈다.
 * 개발용 허용 호스트는 검사하지 않는다.
 */
@Component
public class TargetGuard {

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Set<String> devAllowedHosts;
    private final Resolver resolver;

    @Autowired
    public TargetGuard(WorkerProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    TargetGuard(WorkerProperties properties, Resolver resolver) {
        this.devAllowedHosts = properties.devAllowedHosts().stream()
            .map(host -> host.strip().toLowerCase(Locale.ROOT))
            .filter(host -> !host.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        this.resolver = resolver;
    }

    /**
     * @param rejectReason  점검하면 안 되는 이유. 점검해도 되면 null
     * @param pinnedAddress 엔진이 연결할 IP. 엔진이 DNS를 다시 조회하면 검사한 뒤에 응답이 바뀔 수 있어서 고정한다.
     *                      개발용 허용 호스트는 Docker 이름 등을 그대로 써야 하므로 null
     */
    public record Inspection(String rejectReason, String pinnedAddress) {
    }

    public Inspection inspect(ClaimedTask task) {
        String host = task.host().toLowerCase(Locale.ROOT);
        if (devAllowedHosts.contains(host)) {
            return new Inspection(null, null);
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            return new Inspection("대상 도메인(" + host + ")을 찾을 수 없어 점검하지 않았습니다.", null);
        }
        for (InetAddress address : addresses) {
            if (!PublicAddressPolicy.isPublic(address)) {
                return new Inspection("대상 도메인이 내부망이나 예약된 주소(" + address.getHostAddress() + ")를 가리켜 점검하지 않았습니다.", null);
            }
        }
        // Docker 기본 네트워크는 IPv6가 꺼져 있으므로 IPv4를 먼저 고른다
        InetAddress pinned = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).findFirst().orElse(addresses[0]);
        return new Inspection(null, pinned.getHostAddress());
    }
}
