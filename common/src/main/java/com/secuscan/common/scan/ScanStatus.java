package com.secuscan.common.scan;

import java.util.Collection;

/**
 * 점검 전체의 상태. 따로 저장해서 바꾸지 않고 엔진별 작업 상태에서 계산한다.
 * 두 군데에서 상태를 바꾸면 서로 어긋날 수 있기 때문이다.
 */
public enum ScanStatus {

    QUEUED,
    RUNNING,
    /** 모든 엔진이 성공 */
    COMPLETED,
    /** 일부 엔진만 성공. 성공한 엔진의 결과는 보여 준다 */
    PARTIAL,
    FAILED,
    CANCELLED;

    public static ScanStatus of(Collection<TaskStatus> tasks) {
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("작업이 없는 점검입니다.");
        }
        if (tasks.stream().allMatch(status -> status == TaskStatus.QUEUED)) {
            return QUEUED;
        }
        if (tasks.stream().anyMatch(status -> !status.isFinished())) {
            return RUNNING;
        }
        long succeeded = tasks.stream().filter(status -> status == TaskStatus.SUCCEEDED).count();
        if (succeeded == tasks.size()) {
            return COMPLETED;
        }
        if (succeeded > 0) {
            return PARTIAL;
        }
        if (tasks.stream().anyMatch(status -> status == TaskStatus.FAILED)) {
            return FAILED;
        }
        return CANCELLED;
    }
}
