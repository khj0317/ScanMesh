package com.scanmesh.api.target.dto;

import com.scanmesh.api.target.entity.VerificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TargetCreateRequest(
    @NotBlank @Size(max = 2048) String url,
    @NotNull VerificationMethod verificationMethod
) {
}
