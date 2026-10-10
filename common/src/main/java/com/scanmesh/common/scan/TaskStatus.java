package com.scanmesh.common.scan;

import java.util.EnumSet;
import java.util.Set;

/**
 * 엔진별 작업의 상태. 허용된 전이만 일어나도록 여기서 한곳에 모아 검사한다.
 * <pre>
 * QUEUED  → RUNNING (워커가 가져감), CANCELLED (사용자 취소)
 * RUNNING → SUCCEEDED (결과 제출), FAILED (오류·재시도 소진), CANCELLED (취소 확인),
 *           QUEUED (하트비트가 끊겼고 재시도가 남음)
 * </pre>
 */
public enum TaskStatus {

    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean canTransitionTo(TaskStatus next) {
        return allowedNext().contains(next);
    }

    /** 허용되지 않은 전이면 예외를 던진다. 엔티티의 상태를 바꾸기 전에 부른다 */
    public TaskStatus transitionTo(TaskStatus next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("작업 상태를 " + this + "에서 " + next + "(으)로 바꿀 수 없습니다.");
        }
        return next;
    }

    private Set<TaskStatus> allowedNext() {
        return switch (this) {
            case QUEUED -> EnumSet.of(RUNNING, CANCELLED);
            case RUNNING -> EnumSet.of(SUCCEEDED, FAILED, CANCELLED, QUEUED);
            case SUCCEEDED, FAILED, CANCELLED -> EnumSet.noneOf(TaskStatus.class);
        };
    }
}
