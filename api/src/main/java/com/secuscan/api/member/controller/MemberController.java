package com.secuscan.api.member.controller;

import com.secuscan.api.auth.security.MemberPrincipal;
import com.secuscan.api.common.exception.BusinessException;
import com.secuscan.api.common.exception.ErrorCode;
import com.secuscan.api.member.dto.MemberResponse;
import com.secuscan.api.member.repository.MemberRepository;
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
