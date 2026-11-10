package com.scanmesh.worker.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 점검 엔진 실행 설정.
 *
 * @param resultsDir            엔진 결과 파일을 남기는 폴더
 * @param dockerNetwork         엔진 컨테이너를 붙일 Docker 네트워크. 비우면 기본 네트워크
 * @param nucleiTemplatesVolume Nuclei 템플릿을 받아 둔 Docker 볼륨
 * @param probeInterval         점검 중 대상이 살아 있는지 확인하는 간격
 * @param probeFailures         이 횟수만큼 연달아 응답이 없으면 점검을 멈춘다
 */
@ConfigurationProperties(prefix = "worker.engines")
public record EngineProperties(
    Path resultsDir,
    String dockerNetwork,
    String nucleiImage,
    String nucleiTemplatesVolume,
    Duration nucleiTimeout,
    String zapImage,
    Duration zapTimeout,
    Duration probeInterval,
    int probeFailures
) {
}
