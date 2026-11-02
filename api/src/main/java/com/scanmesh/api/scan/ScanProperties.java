package com.scanmesh.api.scan;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 작업 큐 설정.
 *
 * @param lease          워커가 작업을 가져가거나 하트비트를 보낼 때마다 받는 임대 시간. 이 시간 동안 소식이 없으면 워커가 죽은 것으로 본다
 * @param maxAttempts    워커가 죽어서 다시 큐에 넣는 것까지 포함한 최대 시도 횟수
 * @param reaperInterval 임대가 끝난 작업을 찾는 주기
 */
@ConfigurationProperties(prefix = "app.scan")
public record ScanProperties(Duration lease, int maxAttempts, Duration reaperInterval) {

    /** 하트비트 하나가 늦거나 빠져도 임대가 끝나지 않도록 임대 시간의 1/3마다 보내게 한다 */
    public Duration heartbeatInterval() {
        return lease.dividedBy(3);
    }
}
