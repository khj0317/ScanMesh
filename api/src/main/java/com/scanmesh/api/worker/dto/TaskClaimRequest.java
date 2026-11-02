package com.scanmesh.api.worker.dto;

import com.scanmesh.common.scan.Engine;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** @param engines 이 워커가 지금 돌릴 수 있는 엔진. 워커 종류에 맞지 않는 엔진은 무시한다 */
public record TaskClaimRequest(@NotEmpty Set<Engine> engines) {
}
