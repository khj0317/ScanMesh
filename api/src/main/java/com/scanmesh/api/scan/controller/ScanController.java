package com.scanmesh.api.scan.controller;

import com.scanmesh.api.auth.security.MemberPrincipal;
import com.scanmesh.api.scan.dto.ScanCreateRequest;
import com.scanmesh.api.scan.dto.ScanResponse;
import com.scanmesh.api.scan.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @PostMapping("/api/targets/{targetId}/scans")
    @ResponseStatus(HttpStatus.CREATED)
    public ScanResponse request(
        @AuthenticationPrincipal MemberPrincipal principal,
        @PathVariable Long targetId,
        @RequestBody(required = false) ScanCreateRequest request
    ) {
        return scanService.request(principal.memberId(), targetId, request == null ? null : request.engines());
    }

    @GetMapping("/api/targets/{targetId}/scans")
    public List<ScanResponse> listByTarget(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long targetId) {
        return scanService.listByTarget(principal.memberId(), targetId);
    }

    @GetMapping("/api/scans/{id}")
    public ScanResponse get(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        return scanService.get(principal.memberId(), id);
    }

    @PostMapping("/api/scans/{id}/cancel")
    public ScanResponse cancel(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        return scanService.cancel(principal.memberId(), id);
    }
}
