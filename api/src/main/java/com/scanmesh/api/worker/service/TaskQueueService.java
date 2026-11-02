package com.scanmesh.api.worker.service;

import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.scan.ScanProperties;
import com.scanmesh.api.scan.entity.ScanTask;
import com.scanmesh.api.scan.repository.ScanTaskRepository;
import com.scanmesh.api.target.entity.Target;
import com.scanmesh.api.worker.dto.HeartbeatResponse;
import com.scanmesh.api.worker.dto.TaskClaimResponse;
import com.scanmesh.api.worker.repository.WorkerRepository;
import com.scanmesh.api.worker.security.WorkerPrincipal;
import com.scanmesh.common.scan.Engine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PostgreSQL 행 잠금으로 만든 작업 큐. 작업 상태와 점검 상태를 같은 트랜잭션에서 바꾸므로
 * 큐와 DB가 서로 어긋날 일이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TaskQueueService {

    /** 임대 정리 한 번에 처리할 최대 작업 수. 많이 밀려 있어도 트랜잭션 하나가 너무 길어지지 않게 한다 */
    static final int REAP_BATCH_SIZE = 100;

    private final ScanTaskRepository scanTaskRepository;
    private final WorkerRepository workerRepository;
    private final ScanProperties properties;
    private final Clock clock;

    /** 가져갈 작업이 없으면 빈 값 */
    public Optional<TaskClaimResponse> claim(WorkerPrincipal worker, Set<Engine> requested) {
        // 메모리가 큰 엔진을 가벼운 워커가 가져가지 않도록, 워커 종류에 맞는 엔진만 남긴다
        List<String> engines = Engine.runnableBy(worker.kind()).stream()
            .filter(requested::contains)
            .map(Engine::name)
            .toList();
        if (engines.isEmpty()) {
            return Optional.empty();
        }
        return scanTaskRepository.lockNextQueued(engines).map(taskId -> {
            ScanTask task = scanTaskRepository.findById(taskId).orElseThrow();
            task.start(workerRepository.getReferenceById(worker.workerId()), clock.instant(), properties.lease());
            Target target = task.getScan().getTarget();
            log.info("작업 배정: task={}, engine={}, worker={}, attempt={}", task.getId(), task.getEngine(), worker.workerId(), task.getAttempt());
            return new TaskClaimResponse(
                task.getId(),
                task.getScan().getId(),
                task.getEngine().name(),
                target.getBaseUrl(),
                target.getHost(),
                task.getAttempt(),
                properties.lease().toSeconds(),
                properties.heartbeatInterval().toSeconds()
            );
        });
    }

    public HeartbeatResponse heartbeat(WorkerPrincipal worker, Long taskId, int progress) {
        ScanTask task = held(worker, taskId);
        task.heartbeat(progress, clock.instant(), properties.lease());
        return new HeartbeatResponse(task.isCancelRequested(), task.getLeaseUntil());
    }

    public void complete(WorkerPrincipal worker, Long taskId) {
        held(worker, taskId).succeed(clock.instant());
    }

    public void fail(WorkerPrincipal worker, Long taskId, String errorMessage) {
        held(worker, taskId).fail(errorMessage, clock.instant());
    }

    /**
     * 하트비트가 끊겨 임대가 끝난 작업을 정리한다. 재시도가 남았으면 다시 큐에 넣고, 아니면 실패로 끝낸다.
     *
     * @return 정리한 작업 수
     */
    public int reapExpiredLeases() {
        Instant now = clock.instant();
        List<Long> expired = scanTaskRepository.lockExpiredLeases(now, REAP_BATCH_SIZE);
        for (ScanTask task : scanTaskRepository.findAllById(expired)) {
            log.warn("작업 임대 만료: task={}, engine={}, attempt={}/{}", task.getId(), task.getEngine(), task.getAttempt(), task.getMaxAttempts());
            task.expireLease(now);
        }
        return expired.size();
    }

    /** 이 워커가 지금 맡고 있는 작업을 잠근다. 임대가 넘어갔거나 이미 끝났으면 워커가 하던 일을 버리게 한다 */
    private ScanTask held(WorkerPrincipal worker, Long taskId) {
        ScanTask task = scanTaskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!task.isHeldBy(worker.workerId())) {
            throw new BusinessException(ErrorCode.TASK_LEASE_LOST);
        }
        return task;
    }
}
