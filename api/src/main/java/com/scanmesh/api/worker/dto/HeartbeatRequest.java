package com.scanmesh.api.worker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record HeartbeatRequest(@Min(0) @Max(100) int progress) {
}
