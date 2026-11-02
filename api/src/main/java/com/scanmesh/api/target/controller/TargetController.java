package com.scanmesh.api.target.controller;

import com.scanmesh.api.auth.security.MemberPrincipal;
import com.scanmesh.api.target.dto.TargetCreateRequest;
import com.scanmesh.api.target.dto.TargetResponse;
import com.scanmesh.api.target.dto.VerificationResponse;
import com.scanmesh.api.target.service.TargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class TargetController {

    private final TargetService targetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TargetResponse create(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody TargetCreateRequest request) {
        return targetService.create(principal.memberId(), request);
    }

    @GetMapping
    public List<TargetResponse> list(@AuthenticationPrincipal MemberPrincipal principal) {
        return targetService.list(principal.memberId());
    }

    @PostMapping("/{id}/verify")
    public VerificationResponse verify(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        return targetService.verify(principal.memberId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        targetService.delete(principal.memberId(), id);
    }
}
