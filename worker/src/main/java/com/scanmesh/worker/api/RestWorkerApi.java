package com.scanmesh.worker.api;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.WorkerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Component
public class RestWorkerApi implements WorkerApi {

    private final RestClient client;

    public RestWorkerApi(RestClient.Builder builder, WorkerProperties properties) {
        this.client = builder
            .baseUrl(properties.apiUrl())
            .defaultHeader("X-Worker-Token", properties.token())
            // 409: 다른 워커에게 넘어갔거나 이미 끝난 작업, 404: 대상이 삭제되어 작업도 함께 지워짐
            .defaultStatusHandler(
                status -> status.value() == HttpStatus.CONFLICT.value() || status.value() == HttpStatus.NOT_FOUND.value(),
                (request, response) -> {
                    Long taskId = taskIdFrom(request.getURI().getPath());
                    if (taskId == null) {
                        throw new IllegalStateException("API 요청 실패: " + response.getStatusCode() + " " + request.getURI());
                    }
                    throw new LeaseLostException(taskId);
                })
            .build();
    }

    @Override
    public Optional<ClaimedTask> claim(Collection<Engine> engines) {
        ResponseEntity<ClaimedTask> response = client.post()
            .uri("/api/worker/tasks/claim")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("engines", engines))
            .retrieve()
            .toEntity(ClaimedTask.class);
        return Optional.ofNullable(response.getBody());
    }

    @Override
    public boolean heartbeat(Long taskId, int progress) {
        HeartbeatResult result = client.post()
            .uri("/api/worker/tasks/{id}/heartbeat", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("progress", progress))
            .retrieve()
            .body(HeartbeatResult.class);
        return result != null && result.cancelRequested();
    }

    @Override
    public void complete(Long taskId) {
        client.post()
            .uri("/api/worker/tasks/{id}/complete", taskId)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void fail(Long taskId, String errorMessage) {
        client.post()
            .uri("/api/worker/tasks/{id}/fail", taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("errorMessage", errorMessage))
            .retrieve()
            .toBodilessEntity();
    }

    /** /api/worker/tasks/{id}/... 에서 id를 꺼낸다 */
    private static Long taskIdFrom(String path) {
        String[] parts = path.split("/");
        try {
            return Long.valueOf(parts[4]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record HeartbeatResult(boolean cancelRequested) {
    }
}
