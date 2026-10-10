package com.scanmesh.worker;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.common.scan.WorkerKind;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 워커 설정. 워커는 API 서버에 먼저 접속해 작업을 가져가므로 API 주소와 워커 토큰만 알면 된다.
 *
 * @param apiUrl       API 서버 주소
 * @param token        워커 전용 토큰 (X-Worker-Token)
 * @param kind         LIGHT면 자체 규칙·Nuclei, HEAVY면 ZAP 작업을 가져간다
 * @param pollInterval 할 일이 없을 때 다시 물어보기까지 기다리는 시간
 */
@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(String apiUrl, String token, WorkerKind kind, Duration pollInterval) {

    public List<Engine> engines() {
        return Engine.runnableBy(kind);
    }
}
