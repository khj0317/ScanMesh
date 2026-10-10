package com.scanmesh.common.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStatusTest {

    @ParameterizedTest
    @CsvSource({
        "QUEUED, RUNNING",
        "QUEUED, CANCELLED",
        "RUNNING, SUCCEEDED",
        "RUNNING, FAILED",
        "RUNNING, CANCELLED",
        "RUNNING, QUEUED",
    })
    void 허용된_전이(TaskStatus from, TaskStatus to) {
        assertThat(from.transitionTo(to)).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
        "QUEUED, SUCCEEDED",
        "QUEUED, FAILED",
        "QUEUED, QUEUED",
        "RUNNING, RUNNING",
    })
    void 건너뛰거나_제자리인_전이는_막는다(TaskStatus from, TaskStatus to) {
        assertThatThrownBy(() -> from.transitionTo(to))
            .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"SUCCEEDED", "FAILED", "CANCELLED"})
    void 끝난_작업은_어떤_상태로도_바뀌지_않는다(TaskStatus finished) {
        assertThat(finished.isFinished()).isTrue();
        for (TaskStatus next : TaskStatus.values()) {
            assertThat(finished.canTransitionTo(next)).isFalse();
        }
    }

    @Test
    void 엔진은_종류에_맞는_워커에서만_돈다() {
        assertThat(Engine.runnableBy(WorkerKind.LIGHT)).containsExactly(Engine.CUSTOM, Engine.NUCLEI);
        assertThat(Engine.runnableBy(WorkerKind.HEAVY)).containsExactly(Engine.ZAP);
    }
}
