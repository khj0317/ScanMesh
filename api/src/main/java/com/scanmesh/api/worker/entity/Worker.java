package com.scanmesh.api.worker.entity;

import com.scanmesh.api.common.entity.BaseTimeEntity;
import com.scanmesh.common.scan.WorkerKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 내 PC에서 점검 엔진을 돌리는 워커. 워커 전용 토큰(X-Worker-Token)으로 API에 접속한다 */
@Entity
@Table(name = "workers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worker extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WorkerKind kind;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    private Instant lastSeenAt;

    public Worker(String name, WorkerKind kind, String tokenHash) {
        this.name = name;
        this.kind = kind;
        this.tokenHash = tokenHash;
    }
}
