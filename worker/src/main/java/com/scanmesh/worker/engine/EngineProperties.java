package com.scanmesh.worker.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 점검 엔진 실행 설정.
 *
 * @param resultsDir            엔진 원본 결과 파일을 남기는 폴더
 * @param resultsRetention      원본 결과 파일을 남겨 두는 기간
 * @param dockerNetwork         출구 프록시를 내보낼 Docker 네트워크. 비우면 기본 네트워크. 개발용 대상이 다른 네트워크에 있을 때 쓴다
 * @param egressNetwork         엔진 컨테이너만 붙는 내부 네트워크(바깥으로 못 나감). 없으면 워커가 만든다
 * @param nucleiTemplatesVolume Nuclei 템플릿을 받아 둔 Docker 볼륨
 * @param probeInterval         점검 중 대상이 살아 있는지 확인하는 간격
 * @param probeFailures         이 횟수만큼 연달아 응답이 없으면 점검을 멈춘다
 */
@ConfigurationProperties(prefix = "worker.engines")
public record EngineProperties(
    Path resultsDir,
    Duration resultsRetention,
    String dockerNetwork,
    String egressNetwork,
    String nucleiImage,
    String nucleiTemplatesVolume,
    Duration nucleiTimeout,
    String zapImage,
    Duration zapTimeout,
    Duration probeInterval,
    int probeFailures
) {
}
