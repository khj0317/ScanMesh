package com.scanmesh.api.worker.controller;

import com.scanmesh.api.worker.dto.HeartbeatRequest;
import com.scanmesh.api.worker.dto.HeartbeatResponse;
import com.scanmesh.api.worker.dto.TaskClaimRequest;
import com.scanmesh.api.worker.dto.TaskClaimResponse;
import com.scanmesh.api.worker.dto.TaskCompleteRequest;
import com.scanmesh.api.worker.dto.TaskFailRequest;
import com.scanmesh.api.worker.security.WorkerPrincipal;
import com.scanmesh.api.worker.service.TaskQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 워커 전용 API. 사용자 JWT가 아니라 X-Worker-Token으로 인증한다 */
@RestController
@RequestMapping("/api/worker/tasks")
@RequiredArgsConstructor
public class WorkerTaskController {

    private final TaskQueueService taskQueueService;

    /** 가져갈 작업이 없으면 204 */
    @PostMapping("/claim")
    public ResponseEntity<TaskClaimResponse> claim(@AuthenticationPrincipal WorkerPrincipal worker, @Valid @RequestBody TaskClaimRequest request) {
        return taskQueueService.claim(worker, request.engines())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/heartbeat")
    public HeartbeatResponse heartbeat(@AuthenticationPrincipal WorkerPrincipal worker, @PathVariable Long id, @Valid @RequestBody HeartbeatRequest request) {
        return taskQueueService.heartbeat(worker, id, request.progress());
    }

    /** 발견 항목을 함께 받는다. 본문이 없으면 발견한 것이 없는 것으로 본다 */
    @PostMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(
        @AuthenticationPrincipal WorkerPrincipal worker,
        @PathVariable Long id,
        @Valid @RequestBody(required = false) TaskCompleteRequest request
    ) {
        taskQueueService.complete(worker, id, request == null ? List.of() : request.toFindings());
    }

    @PostMapping("/{id}/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@AuthenticationPrincipal WorkerPrincipal worker, @PathVariable Long id, @Valid @RequestBody TaskFailRequest request) {
        taskQueueService.fail(worker, id, request.errorMessage());
    }
}
