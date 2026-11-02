package com.scanmesh.api.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 점검과 워커 테스트에서 쓰는 준비 작업: 인증된 대상, 점검 요청, 워커 등록 */
public abstract class ScanIntegrationTest extends IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 대상을 등록하고, 소유 인증은 이 테스트의 관심사가 아니므로 DB에서 바로 인증된 것으로 바꾼다 */
    protected long verifiedTarget(String token) throws Exception {
        long id = unverifiedTarget(token);
        jdbcTemplate.update("update targets set verified_at = now() where id = ?", id);
        return id;
    }

    protected long unverifiedTarget(String token) throws Exception {
        String host = "site-" + UUID.randomUUID().toString().substring(0, 8) + ".example.com";
        String json = mockMvc.perform(post("/api/targets")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"url":"https://%s","verificationMethod":"DNS_TXT"}
                    """.formatted(host)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return idOf(json);
    }

    /** @param engines 비우면 본문 없이 요청해서 모든 엔진으로 점검한다 */
    protected ResultActions requestScan(String token, long targetId, String... engines) throws Exception {
        var request = post("/api/targets/" + targetId + "/scans").header("Authorization", bearer(token));
        if (engines.length > 0) {
            String list = Arrays.stream(engines).map(e -> "\"" + e + "\"").collect(Collectors.joining(","));
            request.contentType(MediaType.APPLICATION_JSON).content("{\"engines\":[" + list + "]}");
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getScan(String token, long scanId) throws Exception {
        return mockMvc.perform(get("/api/scans/" + scanId).header("Authorization", bearer(token)));
    }

    /** 관리자 계정을 만들어 워커를 등록하고 워커 토큰을 돌려준다 */
    protected String registerWorker(String kind) throws Exception {
        String json = mockMvc.perform(post("/api/admin/workers")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"test-%s","kind":"%s"}
                    """.formatted(kind.toLowerCase(), kind)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return field(json, "token");
    }

    /** 관리자 가입 화면은 없으므로 DB에서 권한을 바꾼 뒤 다시 로그인한다 (권한은 토큰에 담긴다) */
    protected String adminToken() throws Exception {
        String email = uniqueEmail();
        signup(email);
        jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", email);
        return field(login(email), "accessToken");
    }

    protected ResultActions workerPost(String workerToken, String path, String json) throws Exception {
        var request = post(path).header("X-Worker-Token", workerToken);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions claim(String workerToken, String... engines) throws Exception {
        String list = Arrays.stream(engines).map(e -> "\"" + e + "\"").collect(Collectors.joining(","));
        return workerPost(workerToken, "/api/worker/tasks/claim", "{\"engines\":[" + list + "]}");
    }

    /**
     * 큐는 모든 테스트가 함께 쓰므로, 다른 테스트가 남긴 작업을 워커가 가져가지 않게 끝난 상태로 치운다.
     * 테스트 클래스는 차례로 실행되므로 이 테스트가 도는 동안 다른 테스트가 작업을 넣지 않는다.
     */
    protected void clearQueue() {
        jdbcTemplate.update("""
            update scan_tasks set status = 'CANCELLED', lease_until = null, finished_at = now()
            where status in ('QUEUED', 'RUNNING')
            """);
    }

    protected static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected static long longField(String json, String path) {
        return ((Number) JsonPath.read(json, "$." + path)).longValue();
    }
}
