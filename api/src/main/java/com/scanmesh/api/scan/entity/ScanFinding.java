package com.scanmesh.api.scan.entity;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.FindingFingerprint;
import com.scanmesh.common.finding.OwaspTop10;
import com.scanmesh.common.finding.Severity;
import com.scanmesh.common.scan.Engine;
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

import java.time.Instant;

/** 엔진 하나가 찾은 문제 하나. 공통 형식(Finding)으로 받아 지문과 OWASP 분류를 붙여 저장한다 */
@Entity
@Table(name = "findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ScanTask task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Engine engine;

    @Column(nullable = false, length = 300)
    private String ruleId;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    private Integer cweId;

    @Column(length = 60)
    private String owaspCategory;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 200)
    private String param;

    @Column(length = 2000)
    private String evidence;

    @Column(length = 4000)
    private String remediation;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * 긴 값은 잘라서 저장한다. 엔진 결과 한 줄이 길다고 작업 전체의 결과를 버리지 않기 위해서다.
     * 지문은 자르기 전 값으로 만들어 같은 문제가 잘린 길이 때문에 갈라지지 않게 한다.
     */
    public ScanFinding(ScanTask task, Finding finding, Instant now) {
        this.scan = task.getScan();
        this.task = task;
        this.engine = task.getEngine();
        this.fingerprint = FindingFingerprint.of(engine, finding);
        this.ruleId = truncate(finding.ruleId(), 300);
        this.title = truncate(finding.title(), 300);
        this.severity = finding.severity();
        this.cweId = finding.cweId();
        this.owaspCategory = OwaspTop10.categoryOf(FindingFingerprint.checkName(engine, finding.ruleId(), finding.param()), finding.cweId());
        this.url = truncate(finding.url(), 2048);
        this.param = truncate(finding.param(), 200);
        this.evidence = truncate(finding.evidence(), 2000);
        this.remediation = truncate(finding.remediation(), 4000);
        this.createdAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
