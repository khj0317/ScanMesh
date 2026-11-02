package com.scanmesh.api.scan.dto;

import com.scanmesh.common.scan.Engine;

import java.util.Set;

/** @param engines 돌릴 엔진. 비우면 모든 엔진으로 점검한다 */
public record ScanCreateRequest(Set<Engine> engines) {
}
