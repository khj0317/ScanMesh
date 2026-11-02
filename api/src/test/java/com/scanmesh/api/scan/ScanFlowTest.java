package com.scanmesh.api.scan;

import com.scanmesh.api.support.ScanIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScanFlowTest extends ScanIntegrationTest {

    @Test
    void 인증하지_않은_대상은_점검할_수_없다() throws Exception {
        String token = memberToken();
        long targetId = unverifiedTarget(token);

        requestScan(token, targetId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TARGET_NOT_VERIFIED"));
    }

    @Test
    void 점검을_요청하면_엔진별_작업이_대기열에_들어간다() throws Exception {
        String token = memberToken();
        long targetId = verifiedTarget(token);

        String json = requestScan(token, targetId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.progress").value(0))
            .andExpect(jsonPath("$.tasks", hasSize(3)))
            .andExpect(jsonPath("$.tasks[0].engine").value("CUSTOM"))
            .andExpect(jsonPath("$.tasks[2].engine").value("ZAP"))
            .andExpect(jsonPath("$.tasks[0].status").value("QUEUED"))
            .andExpect(jsonPath("$.tasks[0].maxAttempts").value(3))
            .andReturn().getResponse().getContentAsString();

        getScan(token, idOf(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetId").value(targetId));
        mockMvc.perform(get("/api/targets/" + targetId + "/scans").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void 엔진을_골라_점검할_수_있다() throws Exception {
        String token = memberToken();
        long targetId = verifiedTarget(token);

        requestScan(token, targetId, "NUCLEI", "CUSTOM")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tasks", hasSize(2)))
            .andExpect(jsonPath("$.tasks[0].engine").value("CUSTOM"))
            .andExpect(jsonPath("$.tasks[1].engine").value("NUCLEI"));
    }

    @Test
    void 끝나지_않은_점검이_있으면_같은_대상을_다시_점검할_수_없다() throws Exception {
        String token = memberToken();
        long targetId = verifiedTarget(token);
        String json = requestScan(token, targetId).andReturn().getResponse().getContentAsString();

        requestScan(token, targetId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SCAN_ALREADY_RUNNING"));

        // 취소해서 끝나면 다시 요청할 수 있다
        mockMvc.perform(post("/api/scans/" + idOf(json) + "/cancel").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.progress").value(100))
            .andExpect(jsonPath("$.finishedAt").exists())
            .andExpect(jsonPath("$.tasks[0].status").value("CANCELLED"));
        requestScan(token, targetId).andExpect(status().isCreated());
    }

    @Test
    void 다른_회원의_점검은_보거나_취소할_수_없다() throws Exception {
        String owner = memberToken();
        String other = memberToken();
        long targetId = verifiedTarget(owner);
        long scanId = idOf(requestScan(owner, targetId).andReturn().getResponse().getContentAsString());

        getScan(other, scanId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
        mockMvc.perform(post("/api/scans/" + scanId + "/cancel").header("Authorization", bearer(other)))
            .andExpect(status().isNotFound());
        requestScan(other, targetId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        mockMvc.perform(get("/api/targets/" + targetId + "/scans").header("Authorization", bearer(other)))
            .andExpect(status().isNotFound());
    }
}
