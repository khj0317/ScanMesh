package com.scanmesh.api.member.dto;

import com.scanmesh.api.member.entity.Member;

import java.time.Instant;

public record MemberResponse(Long id, String email, String name, String role, Instant createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getRole().name(), member.getCreatedAt());
    }
}
