package com.scanmesh.api.target.entity;

import com.scanmesh.api.common.entity.BaseTimeEntity;
import com.scanmesh.api.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.Instant;

/** 점검 대상 사이트. 소유 인증을 마쳐야(verifiedAt != null) 점검을 요청할 수 있다 */
@Entity
@Table(name = "targets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Target extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 2048)
    private String baseUrl;

    @Column(nullable = false, length = 253)
    private String host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationMethod verificationMethod;

    @Column(nullable = false, length = 64)
    private String verificationToken;

    private Instant verifiedAt;

    private Instant lastVerificationAttemptAt;

    public Target(Member member, String baseUrl, String host, VerificationMethod verificationMethod, String verificationToken) {
        this.member = member;
        this.baseUrl = baseUrl;
        this.host = host;
        this.verificationMethod = verificationMethod;
        this.verificationToken = verificationToken;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void markVerified(Instant now) {
        if (verifiedAt == null) {
            this.verifiedAt = now;
        }
    }

    /** 스킴, 호스트, 포트까지만 (예: https://example.com:8443). 인증 파일은 경로와 상관없이 여기에 둔다 */
    public String origin() {
        URI uri = URI.create(baseUrl);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
