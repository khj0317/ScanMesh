package com.scanmesh.worker.task;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.common.scan.WorkerKind;
import com.scanmesh.worker.WorkerProperties;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.api.WorkerApi;
import com.scanmesh.worker.engine.EngineRunner;
import com.scanmesh.worker.engine.TaskContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TaskLoopTest {

    @Test
    void 작업_루프는_데몬이_아닌_스레드로_돌아_프로세스를_붙잡는다() {
        WorkerProperties properties = new WorkerProperties("http://api", "token", WorkerKind.LIGHT, Duration.ofHours(1), List.of());
        TaskLoop loop = new TaskLoop(new IdleApi(), properties, new TargetGuard(properties), List.of(new CustomStub()));

        loop.start();
        try {
            Thread thread = Thread.getAllStackTraces().keySet().stream()
                .filter(candidate -> candidate.getName().equals("task-loop"))
                .findFirst().orElseThrow();
            assertThat(thread.isDaemon()).isFalse();
        } finally {
            loop.stop();
        }
    }

    private static final class IdleApi implements WorkerApi {

        @Override
        public Optional<ClaimedTask> claim(Collection<Engine> engines) {
            return Optional.empty();
        }

        @Override
        public boolean heartbeat(Long taskId, int progress) {
            return false;
        }

        @Override
        public void complete(Long taskId) {
        }

        @Override
        public void fail(Long taskId, String errorMessage) {
        }
    }

    private static final class CustomStub implements EngineRunner {

        @Override
        public Engine engine() {
            return Engine.CUSTOM;
        }

        @Override
        public void run(ClaimedTask task, TaskContext context) {
        }
    }
}
