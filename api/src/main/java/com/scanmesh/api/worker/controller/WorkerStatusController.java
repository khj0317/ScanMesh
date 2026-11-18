package com.scanmesh.api.worker.controller;

import com.scanmesh.api.worker.dto.WorkerStatusResponse;
import com.scanmesh.api.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 사용자가 쓴다. 워커 전용 API(/api/worker/**)와 경로가 다르다 */
@RestController
@RequiredArgsConstructor
public class WorkerStatusController {

    private final WorkerService workerService;

    @GetMapping("/api/workers/status")
    public WorkerStatusResponse status() {
        return workerService.status();
    }
}
