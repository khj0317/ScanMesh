package com.scanmesh.api.member.controller;

import com.scanmesh.api.auth.security.MemberPrincipal;
import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.member.dto.MemberResponse;
import com.scanmesh.api.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public MemberResponse me(@AuthenticationPrincipal MemberPrincipal principal) {
        return memberRepository.findById(principal.memberId())
            .map(MemberResponse::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
