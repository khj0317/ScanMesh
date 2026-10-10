package com.scanmesh.common.scan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.scanmesh.common.scan.TaskStatus.CANCELLED;
import static com.scanmesh.common.scan.TaskStatus.FAILED;
import static com.scanmesh.common.scan.TaskStatus.QUEUED;
import static com.scanmesh.common.scan.TaskStatus.RUNNING;
import static com.scanmesh.common.scan.TaskStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanStatusTest {

    @Test
    void 아무_작업도_시작하지_않았으면_대기() {
        assertThat(ScanStatus.of(List.of(QUEUED, QUEUED, QUEUED))).isEqualTo(ScanStatus.QUEUED);
    }

    @Test
    void 하나라도_시작했고_남은_작업이_있으면_실행_중() {
        assertThat(ScanStatus.of(List.of(SUCCEEDED, QUEUED, QUEUED))).isEqualTo(ScanStatus.RUNNING);
        assertThat(ScanStatus.of(List.of(RUNNING, QUEUED, FAILED))).isEqualTo(ScanStatus.RUNNING);
    }

    @Test
    void 모두_성공하면_완료() {
        assertThat(ScanStatus.of(List.of(SUCCEEDED, SUCCEEDED, SUCCEEDED))).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    void ZAP만_실패해도_나머지_결과가_있으면_부분_완료() {
        assertThat(ScanStatus.of(List.of(SUCCEEDED, SUCCEEDED, FAILED))).isEqualTo(ScanStatus.PARTIAL);
        assertThat(ScanStatus.of(List.of(SUCCEEDED, CANCELLED, CANCELLED))).isEqualTo(ScanStatus.PARTIAL);
    }

    @Test
    void 성공한_작업이_없으면_실패_또는_취소() {
        assertThat(ScanStatus.of(List.of(FAILED, CANCELLED, FAILED))).isEqualTo(ScanStatus.FAILED);
        assertThat(ScanStatus.of(List.of(CANCELLED, CANCELLED, CANCELLED))).isEqualTo(ScanStatus.CANCELLED);
    }

    @Test
    void 작업이_없는_점검은_계산할_수_없다() {
        assertThatThrownBy(() -> ScanStatus.of(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
