package com.scanmesh.worker.task;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.WorkerProperties;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.api.WorkerApi;
import com.scanmesh.worker.engine.EngineRunner;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 워커의 본체. API 서버에 작업을 물어보고, 있으면 실행하고, 없으면 잠시 기다렸다 다시 묻는다.
 * 워커가 먼저 접속하는 방식이라 내 PC의 포트를 열 필요가 없다. 작업은 한 번에 하나씩 실행한다.
 */
@Slf4j
@Component
public class TaskLoop {

    private final WorkerApi api;
    private final WorkerProperties properties;
    private final Map<Engine, EngineRunner> runners = new EnumMap<>(Engine.class);
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("heartbeat").factory());
    private final TaskExecutor executor;
    private volatile boolean running;
    private Thread loopThread;

    public TaskLoop(WorkerApi api, WorkerProperties properties, List<EngineRunner> engineRunners) {
        this.api = api;
        this.properties = properties;
        // 워커 종류에 맞는 엔진만 맡는다 (가벼운 워커가 ZAP을 돌리지 않게)
        for (EngineRunner runner : engineRunners) {
            if (properties.engines().contains(runner.engine())) {
                runners.put(runner.engine(), runner);
            }
        }
        this.executor = new TaskExecutor(api, heartbeats, Duration.ofSeconds(2));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("워커 시작: kind={}, engines={}, api={}", properties.kind(), runners.keySet(), properties.apiUrl());
        if (properties.token() == null || properties.token().isBlank()) {
            log.warn("WORKER_TOKEN이 없어 작업을 가져오지 않습니다. 관리자 API(POST /api/admin/workers)로 워커를 등록하세요.");
            return;
        }
        if (runners.isEmpty()) {
            log.warn("{} 워커가 실행할 수 있는 엔진이 아직 없어 작업을 가져오지 않습니다.", properties.kind());
            return;
        }
        running = true;
        loopThread = Thread.ofVirtual().name("task-loop").start(this::loop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (loopThread != null) {
            loopThread.interrupt();
        }
        heartbeats.shutdownNow();
    }

    private void loop() {
        while (running) {
            boolean worked;
            try {
                worked = runOnce();
            } catch (RuntimeException e) {
                log.warn("작업 가져오기 실패. {} 뒤 다시 시도합니다.", properties.pollInterval(), e);
                worked = false;
            }
            if (!worked && !sleep(properties.pollInterval())) {
                return;
            }
        }
    }

    /** @return 작업을 하나 실행했으면 true. 바로 다음 작업을 물어본다 */
    boolean runOnce() {
        Optional<ClaimedTask> claimed = api.claim(runners.keySet());
        if (claimed.isEmpty()) {
            return false;
        }
        ClaimedTask task = claimed.get();
        log.info("작업 시작: task={}, engine={}, target={}, attempt={}", task.taskId(), task.engine(), task.targetUrl(), task.attempt());
        executor.execute(task, runners.get(task.engine()));
        return true;
    }

    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
