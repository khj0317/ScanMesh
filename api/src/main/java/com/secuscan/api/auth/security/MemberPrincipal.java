package com.secuscan.api.auth.security;

import com.secuscan.api.member.entity.MemberRole;

/** 액세스 토큰에서 꺼낸 로그인 정보. 요청마다 DB를 조회하지 않도록 토큰에 담긴 값만 쓴다 */
public record MemberPrincipal(Long memberId, MemberRole role) {
}
