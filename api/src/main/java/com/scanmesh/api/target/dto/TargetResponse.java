package com.scanmesh.api.target.dto;

import com.scanmesh.api.target.entity.Target;
import com.scanmesh.api.target.service.OwnershipVerifier;

import java.time.Instant;

public record TargetResponse(
    Long id,
    String baseUrl,
    String host,
    boolean verified,
    Instant verifiedAt,
    Verification verification,
    Instant createdAt
) {

    /** 인증 방법별로 사용자가 어디에 무엇을 올려야 하는지 */
    public record Verification(String method, String token, String dnsRecordName, String fileUrl) {
    }

    public static TargetResponse from(Target target) {
        Verification verification = new Verification(
            target.getVerificationMethod().name(),
            target.getVerificationToken(),
            OwnershipVerifier.dnsRecordName(target.getHost()),
            OwnershipVerifier.fileUrl(target.origin())
        );
        return new TargetResponse(
            target.getId(),
            target.getBaseUrl(),
            target.getHost(),
            target.isVerified(),
            target.getVerifiedAt(),
            verification,
            target.getCreatedAt()
        );
    }
}
