package com.scanmesh.api.worker;

import com.scanmesh.api.support.ScanIntegrationTest;
import com.scanmesh.api.worker.service.TaskQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerQueueTest extends ScanIntegrationTest {

    @Autowired
    private TaskQueueService taskQueueService;

    @BeforeEach
    void emptyQueue() {
        clearQueue();
    }

    @Test
    void 워커_등록은_관리자만_할_수_있다() throws Exception {
        mockMvc.perform(post("/api/admin/workers")
                .header("Authorization", bearer(memberToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"w\",\"kind\":\"LIGHT\"}"))
            .andExpect(status().isForbidden());

        String workerToken = registerWorker("LIGHT");
        assertThat(workerToken).hasSizeGreaterThan(40);
        mockMvc.perform(get("/api/admin/workers").header("Authorization", bearer(adminToken())))
            .andExpect(status().isOk());
    }

    @Test
    void 워커_API는_워커_토큰으로만_쓸_수_있다() throws Exception {
        claim("wrong-token", "CUSTOM").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/worker/tasks/claim").contentType(MediaType.APPLICATION_JSON).content("{\"engines\":[\"CUSTOM\"]}"))
            .andExpect(status().isUnauthorized());
        // 사용자 JWT로는 워커 행세를 할 수 없다
        mockMvc.perform(post("/api/worker/tasks/claim")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"engines\":[\"CUSTOM\"]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 워커는_자기_종류에_맞는_엔진의_작업만_가져간다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM", "ZAP").andReturn().getResponse().getContentAsString());
        String light = registerWorker("LIGHT");
        String heavy = registerWorker("HEAVY");

        // 가벼운 워커가 ZAP을 달라고 해도 주지 않는다
        claim(light, "ZAP").andExpect(status().isNoContent());
        claim(light, "CUSTOM", "NUCLEI", "ZAP")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("CUSTOM"))
            .andExpect(jsonPath("$.scanId").value(scanId))
            .andExpect(jsonPath("$.targetUrl").exists())
            .andExpect(jsonPath("$.attempt").value(1))
            .andExpect(jsonPath("$.leaseSeconds").value(90))
            .andExpect(jsonPath("$.heartbeatIntervalSeconds").value(30));
        claim(light, "CUSTOM", "NUCLEI").andExpect(status().isNoContent());
        claim(heavy, "ZAP").andExpect(jsonPath("$.engine").value("ZAP"));

        getScan(user, scanId)
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.tasks[0].status").value("RUNNING"));
    }

    @Test
    void 하트비트로_진행률을_알리고_결과를_보고한다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM", "NUCLEI").andReturn().getResponse().getContentAsString());
        String worker = registerWorker("LIGHT");

        long first = claimedTaskId(worker);
        workerPost(worker, "/api/worker/tasks/" + first + "/heartbeat", "{\"progress\":40}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelRequested").value(false))
            .andExpect(jsonPath("$.leaseUntil").exists());
        getScan(user, scanId)
            .andExpect(jsonPath("$.tasks[0].progress").value(40))
            .andExpect(jsonPath("$.progress").value(20));

        workerPost(worker, "/api/worker/tasks/" + first + "/complete", null).andExpect(status().isNoContent());
        // 끝난 작업에는 더 보고할 수 없다
        workerPost(worker, "/api/worker/tasks/" + first + "/heartbeat", "{\"progress\":50}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TASK_LEASE_LOST"));

        long second = claimedTaskId(worker);
        workerPost(worker, "/api/worker/tasks/" + second + "/fail", "{\"errorMessage\":\"대상이 응답하지 않음\"}")
            .andExpect(status().isNoContent());

        // 하나만 성공했으므로 일부 완료
        getScan(user, scanId)
            .andExpect(jsonPath("$.status").value("PARTIAL"))
            .andExpect(jsonPath("$.finishedAt").exists())
            .andExpect(jsonPath("$.tasks[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.tasks[1].status").value("FAILED"))
            .andExpect(jsonPath("$.tasks[1].errorMessage").value("대상이 응답하지 않음"));
    }

    @Test
    void 다른_워커의_작업에는_보고할_수_없다() throws Exception {
        String user = memberToken();
        requestScan(user, verifiedTarget(user), "CUSTOM");
        long taskId = claimedTaskId(registerWorker("LIGHT"));

        workerPost(registerWorker("LIGHT"), "/api/worker/tasks/" + taskId + "/complete", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TASK_LEASE_LOST"));
    }

    @Test
    void 실행_중에_취소하면_워커가_하트비트로_알고_멈춘다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM", "NUCLEI").andReturn().getResponse().getContentAsString());
        String worker = registerWorker("LIGHT");
        long running = claimedTaskId(worker);

        mockMvc.perform(post("/api/scans/" + scanId + "/cancel").header("Authorization", bearer(user)))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.tasks[0].cancelRequested").value(true))
            .andExpect(jsonPath("$.tasks[1].status").value("CANCELLED"));

        workerPost(worker, "/api/worker/tasks/" + running + "/heartbeat", "{\"progress\":10}")
            .andExpect(jsonPath("$.cancelRequested").value(true));
        workerPost(worker, "/api/worker/tasks/" + running + "/fail", "{\"errorMessage\":\"취소됨\"}")
            .andExpect(status().isNoContent());

        getScan(user, scanId)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.tasks[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.tasks[0].errorMessage").doesNotExist());
    }

    @Test
    void 하트비트가_끊긴_작업은_다시_큐에_들어가고_재시도를_다_쓰면_실패한다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM").andReturn().getResponse().getContentAsString());
        String deadWorker = registerWorker("LIGHT");
        String worker = registerWorker("LIGHT");

        long taskId = claimedTaskId(deadWorker);
        expireLease(taskId);
        assertThat(taskQueueService.reapExpiredLeases()).isEqualTo(1);
        // 작업이 모두 다시 대기 중이므로 점검도 워커를 기다리는 상태로 돌아간다
        getScan(user, scanId)
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.tasks[0].status").value("QUEUED"))
            .andExpect(jsonPath("$.tasks[0].attempt").value(1));

        // 다른 워커가 이어받고, 죽은 줄 알았던 워커가 뒤늦게 보고하면 거절한다
        claim(worker, "CUSTOM").andExpect(jsonPath("$.taskId").value(taskId)).andExpect(jsonPath("$.attempt").value(2));
        workerPost(deadWorker, "/api/worker/tasks/" + taskId + "/complete", null)
            .andExpect(status().isConflict());

        expireLease(taskId);
        taskQueueService.reapExpiredLeases();
        claim(worker, "CUSTOM").andExpect(jsonPath("$.attempt").value(3));
        expireLease(taskId);
        taskQueueService.reapExpiredLeases();

        getScan(user, scanId)
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.tasks[0].status").value("FAILED"))
            .andExpect(jsonPath("$.tasks[0].attempt").value(3))
            .andExpect(jsonPath("$.tasks[0].errorMessage").exists());
        claim(worker, "CUSTOM").andExpect(status().isNoContent());
    }

    @Test
    void 워커_여러_대가_동시에_가져가도_같은_작업을_두_번_주지_않는다() throws Exception {
        int scans = 10;
        for (int i = 0; i < scans; i++) {
            String user = memberToken();
            requestScan(user, verifiedTarget(user), "CUSTOM").andExpect(status().isCreated());
        }
        List<String> workers = new ArrayList<>();
        for (int i = 0; i < scans + 5; i++) {
            workers.add(registerWorker("LIGHT"));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(workers.size())) {
            for (String worker : workers) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return claim(worker, "CUSTOM").andReturn();
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            Set<Long> claimed = new HashSet<>();
            int empty = 0;
            for (Future<MvcResult> result : results) {
                MvcResult response = result.get();
                if (response.getResponse().getStatus() == 204) {
                    empty++;
                } else {
                    assertThat(response.getResponse().getStatus()).isEqualTo(200);
                    assertThat(claimed.add(longField(response.getResponse().getContentAsString(), "taskId"))).isTrue();
                }
            }
            assertThat(claimed).hasSize(scans);
            assertThat(empty).isEqualTo(5);
        }
    }

    @Test
    void 관리자_화면에서_워커가_온라인인지_본다() throws Exception {
        String worker = registerWorker("HEAVY");
        claim(worker, "ZAP");

        mockMvc.perform(get("/api/admin/workers").header("Authorization", bearer(adminToken())))
            .andExpect(jsonPath("$[?(@.online == true)]", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    private long claimedTaskId(String worker) throws Exception {
        String json = claim(worker, "CUSTOM", "NUCLEI").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return longField(json, "taskId");
    }

    private void expireLease(long taskId) {
        jdbcTemplate.update("update scan_tasks set lease_until = now() - interval '1 minute' where id = ?", taskId);
    }
}
