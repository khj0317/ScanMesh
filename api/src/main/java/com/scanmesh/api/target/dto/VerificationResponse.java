package com.scanmesh.api.target.dto;

/**
 * 소유 인증 확인 결과. 실패도 정상 응답(200)으로 돌려준다. 요청 자체는 올바르고,
 * 사용자가 DNS나 파일을 고친 뒤 다시 확인하는 흐름이라 detail에 고칠 점을 담는다.
 */
public record VerificationResponse(boolean verified, String detail, TargetResponse target) {
}
