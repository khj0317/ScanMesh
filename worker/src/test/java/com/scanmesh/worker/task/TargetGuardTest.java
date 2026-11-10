package com.scanmesh.worker.task;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.common.scan.WorkerKind;
import com.scanmesh.worker.WorkerProperties;
import com.scanmesh.worker.api.ClaimedTask;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TargetGuardTest {

    private static final Map<String, String> DNS = Map.of(
        "example.com", "93.184.215.14",
        "rebound.example.com", "10.0.0.5",
        "juice-shop", "172.18.0.2"
    );

    private final TargetGuard guard = new TargetGuard(
        new WorkerProperties("http://api", "t", WorkerKind.LIGHT, Duration.ofSeconds(10), List.of("Juice-Shop")),
        host -> {
            String ip = DNS.get(host);
            if (ip == null) {
                throw new UnknownHostException(host);
            }
            return new InetAddress[]{InetAddress.getByName(ip)};
        });

    @Test
    void 공인_주소를_가리키는_대상은_점검한다() {
        assertThat(guard.inspect(task("example.com"))).isEqualTo(new TargetGuard.Inspection(null, "93.184.215.14"));
    }

    @Test
    void 등록_뒤에_DNS가_내부_주소로_바뀐_대상은_점검하지_않는다() {
        assertThat(guard.inspect(task("rebound.example.com")).rejectReason()).contains("10.0.0.5");
    }

    @Test
    void 찾을_수_없는_도메인은_점검하지_않는다() {
        assertThat(guard.inspect(task("gone.example.com")).rejectReason()).isNotNull();
    }

    @Test
    void 개발용_허용_호스트는_내부_주소여도_점검한다() {
        assertThat(guard.inspect(task("juice-shop"))).isEqualTo(new TargetGuard.Inspection(null, null));
    }

    private static ClaimedTask task(String host) {
        return new ClaimedTask(1L, 1L, Engine.CUSTOM, "http://" + host + "/", host, 1, 90, 30);
    }
}
