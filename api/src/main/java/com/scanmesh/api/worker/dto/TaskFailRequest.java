package com.scanmesh.api.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskFailRequest(@NotBlank @Size(max = 1000) String errorMessage) {
}
