package com.scanmesh.worker.task;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.api.LeaseLostException;
import com.scanmesh.worker.api.WorkerApi;
import com.scanmesh.worker.engine.EngineRunner;
import com.scanmesh.worker.engine.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutorTest {

    private static final ClaimedTask TASK = new ClaimedTask(7L, 1L, Engine.CUSTOM, "https://example.com/", "example.com", 1, 3, 1);

    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();
    private final FakeApi api = new FakeApi();
    private final TaskExecutor executor = new TaskExecutor(api, heartbeats, Duration.ofMillis(10));

    @AfterEach
    void shutdown() {
        heartbeats.shutdownNow();
    }

    @Test
    void 엔진이_끝나면_완료를_보고한다() {
        TaskExecutor.Outcome outcome = executor.execute(TASK, runner((task, context) -> context.reportProgress(100)));

        assertThat(outcome).isEqualTo(TaskExecutor.Outcome.SUCCEEDED);
        assertThat(api.calls).containsExactly("complete:7");
    }

    @Test
    void 엔진이_실패하면_오류_메시지와_함께_실패를_보고한다() {
        TaskExecutor.Outcome outcome = executor.execute(TASK, runner((task, context) -> {
            throw new IllegalStateException("nuclei 종료 코드 2");
        }));

        assertThat(outcome).isEqualTo(TaskExecutor.Outcome.FAILED);
        assertThat(api.calls).containsExactly("fail:7:nuclei 종료 코드 2");
    }

    @Test
    void 실행_중에_하트비트로_진행률을_보내고_취소_요청을_받으면_엔진을_멈춘다() {
        api.cancelAfterHeartbeats = 2;

        TaskExecutor.Outcome outcome = executor.execute(TASK, runner((task, context) -> {
            context.reportProgress(30);
            Thread.sleep(Duration.ofSeconds(30));
        }));

        assertThat(outcome).isEqualTo(TaskExecutor.Outcome.CANCELLED);
        assertThat(api.calls).startsWith("heartbeat:7:30", "heartbeat:7:30");
        assertThat(api.calls.getLast()).isEqualTo("fail:7:사용자가 점검을 취소했습니다.");
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void 임대를_잃으면_엔진을_멈추고_결과를_보내지_않는다() {
        api.leaseLost = true;

        TaskExecutor.Outcome outcome = executor.execute(TASK, runner((task, context) -> Thread.sleep(Duration.ofSeconds(30))));

        assertThat(outcome).isEqualTo(TaskExecutor.Outcome.LEASE_LOST);
        assertThat(api.calls).containsExactly("heartbeat:7:0");
    }

    @Test
    void 결과_보고가_잠깐_실패하면_다시_보낸다() {
        api.failReports = 2;

        TaskExecutor.Outcome outcome = executor.execute(TASK, runner((task, context) -> { }));

        assertThat(outcome).isEqualTo(TaskExecutor.Outcome.SUCCEEDED);
        assertThat(api.calls).containsExactly("complete:7", "complete:7", "complete:7");
    }

    private static EngineRunner runner(Body body) {
        return new EngineRunner() {
            @Override
            public Engine engine() {
                return Engine.CUSTOM;
            }

            @Override
            public void run(ClaimedTask task, TaskContext context) throws Exception {
                body.run(task, context);
            }
        };
    }

    @FunctionalInterface
    private interface Body {
        void run(ClaimedTask task, TaskContext context) throws Exception;
    }

    private static final class FakeApi implements WorkerApi {

        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile int cancelAfterHeartbeats = Integer.MAX_VALUE;
        volatile boolean leaseLost;
        volatile int failReports;
        private int heartbeats;

        @Override
        public Optional<ClaimedTask> claim(Collection<Engine> engines) {
            return Optional.empty();
        }

        @Override
        public synchronized boolean heartbeat(Long taskId, int progress) {
            calls.add("heartbeat:" + taskId + ":" + progress);
            if (leaseLost) {
                throw new LeaseLostException(taskId);
            }
            return ++heartbeats >= cancelAfterHeartbeats;
        }

        @Override
        public void complete(Long taskId) {
            calls.add("complete:" + taskId);
            if (failReports-- > 0) {
                throw new IllegalStateException("네트워크 오류");
            }
        }

        @Override
        public void fail(Long taskId, String errorMessage) {
            calls.add("fail:" + taskId + ":" + errorMessage);
        }
    }
}
