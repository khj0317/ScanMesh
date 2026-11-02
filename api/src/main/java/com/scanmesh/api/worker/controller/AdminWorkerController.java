package com.scanmesh.api.worker.controller;

import com.scanmesh.api.worker.dto.WorkerCreateRequest;
import com.scanmesh.api.worker.dto.WorkerCreatedResponse;
import com.scanmesh.api.worker.dto.WorkerResponse;
import com.scanmesh.api.worker.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자만 쓴다 (SecurityConfig에서 /api/admin/** 을 ADMIN으로 막음) */
@RestController
@RequestMapping("/api/admin/workers")
@RequiredArgsConstructor
public class AdminWorkerController {

    private final WorkerService workerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkerCreatedResponse register(@Valid @RequestBody WorkerCreateRequest request) {
        return workerService.register(request);
    }

    @GetMapping
    public List<WorkerResponse> list() {
        return workerService.list();
    }
}
