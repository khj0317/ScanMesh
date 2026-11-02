package com.scanmesh.api.scan.entity;

import com.scanmesh.api.common.entity.BaseTimeEntity;
import com.scanmesh.api.member.entity.Member;
import com.scanmesh.api.target.entity.Target;
import com.scanmesh.common.scan.Engine;
import com.scanmesh.common.scan.ScanStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 점검 요청 하나. 엔진별 작업(ScanTask)으로 나뉘고, 상태는 작업 상태들에서 계산한다 */
@Entity
@Table(name = "scans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scan extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private Target target;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private Member requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant finishedAt;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<ScanTask> tasks = new ArrayList<>();

    public Scan(Target target, Member requestedBy, Collection<Engine> engines, int maxAttempts, Instant now) {
        this.target = target;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        for (Engine engine : engines) {
            tasks.add(new ScanTask(this, engine, maxAttempts));
        }
    }

    public ScanStatus status() {
        return ScanStatus.of(tasks.stream().map(ScanTask::getStatus).toList());
    }

    /** 끝난 작업은 100으로 쳐서 평균을 낸다. 실패·취소된 작업 때문에 진행률이 멈춰 보이지 않게 한다 */
    public int progress() {
        return (int) Math.round(tasks.stream()
            .mapToInt(task -> task.getStatus().isFinished() ? 100 : task.getProgress())
            .average()
            .orElse(0));
    }

    /** 작업 상태가 바뀔 때마다 부른다. 마지막 작업이 끝났으면 점검 종료 시각을 남긴다 */
    public void refreshFinishedAt(Instant now) {
        boolean allFinished = tasks.stream().allMatch(task -> task.getStatus().isFinished());
        if (allFinished && finishedAt == null) {
            this.finishedAt = now;
        }
    }
}
