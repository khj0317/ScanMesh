package com.scanmesh.api.worker.dto;

import com.scanmesh.common.scan.WorkerKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerCreateRequest(@NotBlank @Size(max = 50) String name, @NotNull WorkerKind kind) {
}
