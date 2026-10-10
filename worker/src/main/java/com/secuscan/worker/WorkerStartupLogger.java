package com.secuscan.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 3주차에 작업을 가져오는 루프가 들어오기 전까지, 워커가 어떤 설정으로 떴는지만 남긴다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerStartupLogger {

    private final WorkerProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void logStartup() {
        log.info("워커 시작: kind={}, engines={}, api={}", properties.kind(), properties.engines(), properties.apiUrl());
    }
}
