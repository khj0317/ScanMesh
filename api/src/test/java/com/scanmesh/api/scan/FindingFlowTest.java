package com.scanmesh.api.scan;

import com.scanmesh.api.support.ScanIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FindingFlowTest extends ScanIntegrationTest {

    @BeforeEach
    void emptyQueue() {
        clearQueue();
    }

    @Test
    void 같은_페이지의_같은_문제는_엔진이_달라도_하나로_묶고_같은_검사는_문제_하나로_접는다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM", "ZAP").andReturn().getResponse().getContentAsString());
        String light = registerWorker("LIGHT");
        String heavy = registerWorker("HEAVY");

        long custom = taskId(claim(light, "CUSTOM").andReturn().getResponse().getContentAsString());
        workerPost(light, "/api/worker/tasks/" + custom + "/complete", """
            {"findings":[
              {"ruleId":"MISSING_CSP","title":"Content-Security-Policy 헤더 없음","severity":"MEDIUM","cweId":693,
               "url":"https://shop.example.com/","remediation":"CSP 헤더를 보내세요."},
              {"ruleId":"SENSITIVE_FILE","title":"환경 변수 파일(.env) 노출","severity":"CRITICAL","cweId":538,
               "url":"https://shop.example.com/.env"}
            ]}
            """).andExpect(status().isNoContent());

        // 점검이 끝나기 전에도 끝난 엔진의 결과는 볼 수 있다
        findings(user, scanId)
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.issues", hasSize(2)));

        long zap = taskId(claim(heavy, "ZAP").andReturn().getResponse().getContentAsString());
        workerPost(heavy, "/api/worker/tasks/" + zap + "/complete", """
            {"findings":[
              {"ruleId":"10038-1","title":"Content Security Policy (CSP) Header Not Set","severity":"MEDIUM","cweId":693,
               "url":"https://SHOP.example.com:443/?q=1","remediation":"Set the header."},
              {"ruleId":"10038-1","title":"Content Security Policy (CSP) Header Not Set","severity":"MEDIUM","cweId":693,
               "url":"https://shop.example.com/login","remediation":"Set the header."},
              {"ruleId":"10010","title":"Cookie No HttpOnly Flag","severity":"LOW","cweId":1004,
               "url":"https://shop.example.com/","param":"token","evidence":"Set-Cookie: token"}
            ]}
            """).andExpect(status().isNoContent());

        findings(user, scanId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.summary.CRITICAL").value(1))
            .andExpect(jsonPath("$.summary.MEDIUM").value(1))
            .andExpect(jsonPath("$.summary.LOW").value(1))
            .andExpect(jsonPath("$.summary.HIGH").value(0))
            .andExpect(jsonPath("$.locationCount").value(4))
            .andExpect(jsonPath("$.issues", hasSize(3)))
            .andExpect(jsonPath("$.issues[*].severity", contains("CRITICAL", "MEDIUM", "LOW")))
            // 문제 하나에 페이지가 여럿이면 위치 목록으로 접는다
            .andExpect(jsonPath("$.issues[1].check").value("missing-csp"))
            .andExpect(jsonPath("$.issues[1].locations", hasSize(2)))
            .andExpect(jsonPath("$.issues[1].engines", contains("CUSTOM", "ZAP")))
            // 묶인 항목은 자체 규칙의 제목과 조치 방법을 대표로 쓴다
            .andExpect(jsonPath("$.issues[1].title").value("Content-Security-Policy 헤더 없음"))
            .andExpect(jsonPath("$.issues[1].remediation").value("CSP 헤더를 보내세요."))
            .andExpect(jsonPath("$.issues[1].locations[0].url").value("https://shop.example.com/"))
            .andExpect(jsonPath("$.issues[1].locations[0].engines", contains("CUSTOM", "ZAP")))
            .andExpect(jsonPath("$.issues[1].locations[0].occurrences").value(2))
            .andExpect(jsonPath("$.issues[1].locations[1].url").value("https://shop.example.com/login"))
            .andExpect(jsonPath("$.issues[1].locations[1].engines", contains("ZAP")))
            // 보안 헤더 문제는 CWE-693이라도 보충 규칙으로 A05에 들어간다
            .andExpect(jsonPath("$.issues[1].owaspCategory").value(org.hamcrest.Matchers.startsWith("A05")))
            .andExpect(jsonPath("$.issues[0].owaspCategory").value(org.hamcrest.Matchers.startsWith("A01")))
            .andExpect(jsonPath("$.issues[2].locations[0].param").value("token"))
            .andExpect(jsonPath("$.issues[2].owaspCategory").value(org.hamcrest.Matchers.startsWith("A05")));
    }

    @Test
    void 긴_값은_잘라서_저장하고_너무_많은_항목은_거절한다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM").andReturn().getResponse().getContentAsString());
        String worker = registerWorker("LIGHT");
        long taskId = taskId(claim(worker, "CUSTOM").andReturn().getResponse().getContentAsString());

        String many = IntStream.range(0, 501)
            .mapToObj(i -> "{\"ruleId\":\"R\",\"title\":\"t\",\"severity\":\"INFO\",\"url\":\"https://shop.example.com/" + i + "\"}")
            .collect(Collectors.joining(","));
        workerPost(worker, "/api/worker/tasks/" + taskId + "/complete", "{\"findings\":[" + many + "]}")
            .andExpect(status().isBadRequest());

        String longTitle = "가".repeat(1000);
        workerPost(worker, "/api/worker/tasks/" + taskId + "/complete", """
            {"findings":[{"ruleId":"R","title":"%s","severity":"HIGH","url":"https://shop.example.com/"}]}
            """.formatted(longTitle)).andExpect(status().isNoContent());

        findings(user, scanId)
            .andExpect(jsonPath("$.issues[0].title").value("가".repeat(300)));
    }

    @Test
    void 결과가_없어도_완료할_수_있고_다른_회원은_결과를_볼_수_없다() throws Exception {
        String user = memberToken();
        long scanId = idOf(requestScan(user, verifiedTarget(user), "CUSTOM").andReturn().getResponse().getContentAsString());
        String worker = registerWorker("LIGHT");
        long taskId = taskId(claim(worker, "CUSTOM").andReturn().getResponse().getContentAsString());

        workerPost(worker, "/api/worker/tasks/" + taskId + "/complete", "{\"findings\":[]}").andExpect(status().isNoContent());

        findings(user, scanId)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.issues", hasSize(0)))
            .andExpect(jsonPath("$.locationCount").value(0))
            .andExpect(jsonPath("$.summary.INFO").value(0));
        findings(memberToken(), scanId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions findings(String token, long scanId) throws Exception {
        return mockMvc.perform(get("/api/scans/" + scanId + "/findings").header("Authorization", bearer(token)));
    }

    private static long taskId(String claimJson) {
        return longField(claimJson, "taskId");
    }
}
