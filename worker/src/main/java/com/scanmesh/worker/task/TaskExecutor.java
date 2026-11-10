package com.scanmesh.worker.task;

import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.api.LeaseLostException;
import com.scanmesh.worker.api.WorkerApi;
import com.scanmesh.worker.engine.EngineRunner;
import com.scanmesh.worker.engine.TaskContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 작업 하나를 실행한다. 엔진이 도는 동안 하트비트를 보내 임대를 늘리고, 사용자가 취소했거나
 * 임대를 잃었으면 엔진 스레드를 인터럽트해서 멈춘다. 끝나면 결과를 API에 보고한다.
 */
@Slf4j
public class TaskExecutor {

    public enum Outcome { SUCCEEDED, FAILED, CANCELLED, LEASE_LOST }

    private static final int REPORT_ATTEMPTS = 3;

    private final WorkerApi api;
    private final ScheduledExecutorService heartbeats;
    private final Duration reportRetryDelay;

    public TaskExecutor(WorkerApi api, ScheduledExecutorService heartbeats, Duration reportRetryDelay) {
        this.api = api;
        this.heartbeats = heartbeats;
        this.reportRetryDelay = reportRetryDelay;
    }

    /** @param pinnedAddress 엔진이 연결할 IP. 개발용 허용 호스트면 null */
    public Outcome execute(ClaimedTask task, EngineRunner runner, String pinnedAddress) {
        Run run = new Run(Thread.currentThread(), pinnedAddress);
        long interval = Math.max(1, task.heartbeatIntervalSeconds());
        ScheduledFuture<?> heartbeat = heartbeats.scheduleWithFixedDelay(
            () -> sendHeartbeat(task, run), interval, interval, TimeUnit.SECONDS);

        Exception error = null;
        try {
            runner.run(task, run);
        } catch (Exception e) {
            error = e;
        } finally {
            heartbeat.cancel(false);
            run.finish();
        }

        if (run.leaseLost) {
            log.warn("임대를 잃어 결과를 보내지 않음: task={}", task.taskId());
            return Outcome.LEASE_LOST;
        }
        if (run.cancelled) {
            // 서버는 취소 요청이 있는 작업의 실패 보고를 취소로 기록한다
            return report(task, Outcome.CANCELLED, () -> api.fail(task.taskId(), "사용자가 점검을 취소했습니다."));
        }
        if (error != null) {
            log.warn("엔진 실패: task={}, engine={}", task.taskId(), task.engine(), error);
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return report(task, Outcome.FAILED, () -> api.fail(task.taskId(), message));
        }
        return report(task, Outcome.SUCCEEDED, () -> api.complete(task.taskId()));
    }

    private void sendHeartbeat(ClaimedTask task, Run run) {
        try {
            if (api.heartbeat(task.taskId(), run.progress.get())) {
                log.info("취소 요청 확인: task={}", task.taskId());
                run.stop(false);
            }
        } catch (LeaseLostException e) {
            run.stop(true);
        } catch (RuntimeException e) {
            // 네트워크가 잠깐 끊긴 정도면 다음 하트비트가 임대를 늘린다. 임대는 하트비트 간격의 3배다
            log.warn("하트비트 실패: task={}", task.taskId(), e);
        }
    }

    /** 결과 보고가 네트워크 문제로 실패하면 몇 번 더 시도한다. 끝내 실패하면 임대가 끝나 다시 큐에 들어간다 */
    private Outcome report(ClaimedTask task, Outcome outcome, Runnable call) {
        for (int attempt = 1; attempt <= REPORT_ATTEMPTS; attempt++) {
            try {
                call.run();
                log.info("작업 종료: task={}, engine={}, outcome={}", task.taskId(), task.engine(), outcome);
                return outcome;
            } catch (LeaseLostException e) {
                log.warn("보고 전에 임대를 잃음: task={}", task.taskId());
                return Outcome.LEASE_LOST;
            } catch (RuntimeException e) {
                log.warn("결과 보고 실패 {}/{}: task={}", attempt, REPORT_ATTEMPTS, task.taskId(), e);
                if (attempt < REPORT_ATTEMPTS && !sleep(reportRetryDelay)) {
                    break;
                }
            }
        }
        return Outcome.LEASE_LOST;
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

    /** 엔진 스레드와 하트비트 스레드가 함께 보는 실행 상태 */
    private static final class Run implements TaskContext {

        private final Thread engineThread;
        private final String pinnedAddress;
        private final AtomicInteger progress = new AtomicInteger();
        private volatile boolean cancelled;
        private volatile boolean leaseLost;
        private boolean finished;

        Run(Thread engineThread, String pinnedAddress) {
            this.engineThread = engineThread;
            this.pinnedAddress = pinnedAddress;
        }

        @Override
        public Optional<String> pinnedAddress() {
            return Optional.ofNullable(pinnedAddress);
        }

        @Override
        public void reportProgress(int value) {
            progress.set(Math.clamp(value, 0, 100));
        }

        @Override
        public boolean isCancelled() {
            return cancelled || leaseLost;
        }

        /** 엔진이 이미 끝났으면 인터럽트하지 않는다. 끝난 뒤에 걸린 인터럽트가 다음 작업을 깨우지 않게 한다 */
        synchronized void stop(boolean lost) {
            if (lost) {
                leaseLost = true;
            } else {
                cancelled = true;
            }
            if (!finished) {
                engineThread.interrupt();
            }
        }

        synchronized void finish() {
            finished = true;
            // 엔진이 인터럽트를 처리하지 않고 돌아왔으면 표시가 남아 있으므로 지운다
            Thread.interrupted();
        }
    }
}
