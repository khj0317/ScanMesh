package com.scanmesh.api.worker.service;

import com.scanmesh.api.common.security.SecretTokens;
import com.scanmesh.api.worker.dto.WorkerCreateRequest;
import com.scanmesh.api.worker.dto.WorkerCreatedResponse;
import com.scanmesh.api.worker.dto.WorkerResponse;
import com.scanmesh.api.worker.entity.Worker;
import com.scanmesh.api.worker.repository.WorkerRepository;
import com.scanmesh.api.worker.security.WorkerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 워커 등록(관리자)과 워커 토큰 인증 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerService {

    /** 이 시간 안에 요청이 있었으면 온라인으로 본다. 할 일이 없는 워커도 기본 10초마다 작업을 물어본다 */
    static final Duration ONLINE_WINDOW = Duration.ofMinutes(1);

    private final WorkerRepository workerRepository;
    private final Clock clock;

    /** 토큰 원문은 이 응답에서 한 번만 보여 주고 저장하지 않는다 */
    @Transactional
    public WorkerCreatedResponse register(WorkerCreateRequest request) {
        String token = SecretTokens.generate();
        Worker worker = workerRepository.save(new Worker(request.name().strip(), request.kind(), SecretTokens.hash(token)));
        return new WorkerCreatedResponse(worker.getId(), worker.getName(), worker.getKind().name(), token);
    }

    public List<WorkerResponse> list() {
        Instant onlineSince = clock.instant().minus(ONLINE_WINDOW);
        return workerRepository.findAll(Sort.by("id")).stream()
            .map(worker -> WorkerResponse.from(worker, onlineSince))
            .toList();
    }

    /** 토큰이 맞으면 마지막 접속 시각을 남긴다. 화면의 '워커 오프라인' 표시에 쓴다 */
    @Transactional
    public Optional<WorkerPrincipal> authenticate(String rawToken) {
        return workerRepository.findByTokenHash(SecretTokens.hash(rawToken))
            .map(worker -> {
                workerRepository.touch(worker.getId(), clock.instant());
                return new WorkerPrincipal(worker.getId(), worker.getKind());
            });
    }
}
