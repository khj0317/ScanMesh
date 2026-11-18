package com.scanmesh.api.worker.dto;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 작업 완료 보고. 워커가 공통 형식(common의 Finding)으로 보낸 발견 항목을 받는다.
 * 긴 값은 저장할 때 자르므로 여기서는 개수와 꼭 있어야 하는 값만 검사한다.
 */
public record TaskCompleteRequest(@Size(max = MAX_FINDINGS) List<@Valid Item> findings) {

    public static final int MAX_FINDINGS = 500;

    public record Item(
        @NotBlank String ruleId,
        @NotBlank String title,
        @NotNull Severity severity,
        Integer cweId,
        @NotBlank String url,
        String param,
        String evidence,
        String remediation
    ) {

        public Finding toFinding() {
            return new Finding(ruleId, title, severity, cweId, url, param, evidence, remediation);
        }
    }

    public List<Finding> toFindings() {
        return findings == null ? List.of() : findings.stream().map(Item::toFinding).toList();
    }
}
