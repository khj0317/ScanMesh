package com.scanmesh.api.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 주기적으로 임대가 끝난 작업을 정리한다. 워커가 꺼지거나 죽어도 작업이 영원히 실행 중으로 남지 않는다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaseReaper {

    private final TaskQueueService taskQueueService;

    @Scheduled(fixedDelayString = "${app.scan.reaper-interval}", initialDelayString = "${app.scan.reaper-interval}")
    public void reap() {
        try {
            int count;
            // 한 번에 다 못 끝냈으면 이어서 처리한다
            do {
                count = taskQueueService.reapExpiredLeases();
            } while (count == TaskQueueService.REAP_BATCH_SIZE);
        } catch (RuntimeException e) {
            // 다음 주기에 다시 시도한다. 예외가 스케줄러 밖으로 나가도 다음 실행은 되지만, 로그는 남긴다
            log.error("작업 임대 정리 실패", e);
        }
    }
}
