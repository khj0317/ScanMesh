package com.scanmesh.api.scan.entity;

import com.scanmesh.api.common.entity.BaseTimeEntity;
import com.scanmesh.api.worker.entity.Worker;
import com.scanmesh.common.scan.Engine;
import com.scanmesh.common.scan.TaskStatus;
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

import java.time.Duration;
import java.time.Instant;

/**
 * 엔진 하나가 맡는 작업. 상태는 TaskStatus의 전이 규칙을 거쳐서만 바꾼다.
 * 워커는 작업을 가져갈 때 임대(leaseUntil)를 받고, 하트비트로 임대를 늘린다.
 */
@Entity
@Table(name = "scan_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanTask extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Engine engine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private int maxAttempts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    private Instant leaseUntil;

    @Column(nullable = false)
    private boolean cancelRequested;

    @Column(nullable = false)
    private int progress;

    @Column(length = MAX_ERROR_LENGTH)
    private String errorMessage;

    private Instant startedAt;

    private Instant finishedAt;

    ScanTask(Scan scan, Engine engine, int maxAttempts) {
        this.scan = scan;
        this.engine = engine;
        this.status = TaskStatus.QUEUED;
        this.maxAttempts = maxAttempts;
    }

    /** 이 워커가 지금 이 작업을 맡고 있는지. 다시 큐에 들어갔다가 다른 워커가 가져갔으면 false */
    public boolean isHeldBy(Long workerId) {
        return status == TaskStatus.RUNNING && worker != null && worker.getId().equals(workerId);
    }

    public void start(Worker worker, Instant now, Duration lease) {
        this.status = status.transitionTo(TaskStatus.RUNNING);
        this.worker = worker;
        this.attempt++;
        this.progress = 0;
        this.errorMessage = null;
        this.leaseUntil = now.plus(lease);
        if (startedAt == null) {
            this.startedAt = now;
        }
    }

    public void heartbeat(int progress, Instant now, Duration lease) {
        this.progress = progress;
        this.leaseUntil = now.plus(lease);
    }

    public void succeed(Instant now) {
        finish(TaskStatus.SUCCEEDED, null, now);
        this.progress = 100;
    }

    /** 사용자가 취소를 요청한 작업이면 실패 대신 취소로 끝낸다. 워커는 취소를 확인하면 실패로 보고한다 */
    public void fail(String errorMessage, Instant now) {
        if (cancelRequested) {
            finish(TaskStatus.CANCELLED, null, now);
        } else {
            finish(TaskStatus.FAILED, errorMessage, now);
        }
    }

    /**
     * 사용자의 취소 요청. 대기 중이면 바로 취소하고, 실행 중이면 표시만 해 두고
     * 워커가 하트비트 응답으로 확인해 멈추게 한다.
     */
    public void requestCancel(Instant now) {
        if (status == TaskStatus.QUEUED) {
            finish(TaskStatus.CANCELLED, null, now);
        } else if (status == TaskStatus.RUNNING) {
            this.cancelRequested = true;
        }
    }

    /** 임대가 끝나도록 하트비트가 없었다. 재시도가 남았으면 다시 큐에 넣는다 */
    public void expireLease(Instant now) {
        if (cancelRequested) {
            finish(TaskStatus.CANCELLED, null, now);
        } else if (attempt < maxAttempts) {
            this.status = status.transitionTo(TaskStatus.QUEUED);
            this.worker = null;
            this.leaseUntil = null;
            this.progress = 0;
        } else {
            finish(TaskStatus.FAILED, "워커가 응답하지 않아 " + maxAttempts + "번 시도 후 중단했습니다.", now);
        }
    }

    private void finish(TaskStatus next, String errorMessage, Instant now) {
        this.status = status.transitionTo(next);
        this.errorMessage = truncate(errorMessage);
        this.leaseUntil = null;
        this.finishedAt = now;
        scan.refreshFinishedAt(now);
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
