package com.scanmesh.worker.api;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.common.scan.WorkerKind;
import com.scanmesh.worker.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestWorkerApiTest {

    private MockRestServiceServer server;
    private RestWorkerApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new RestWorkerApi(builder, new WorkerProperties("http://api.test", "secret", WorkerKind.LIGHT, Duration.ofSeconds(10)));
    }

    @Test
    void 워커_토큰을_붙여_작업을_가져온다() {
        server.expect(requestTo("http://api.test/api/worker/tasks/claim"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Worker-Token", "secret"))
            .andExpect(content().json("{\"engines\":[\"CUSTOM\"]}"))
            .andRespond(withSuccess("""
                {"taskId":7,"scanId":1,"engine":"CUSTOM","targetUrl":"https://example.com/","host":"example.com",
                 "attempt":1,"leaseSeconds":90,"heartbeatIntervalSeconds":30}
                """, MediaType.APPLICATION_JSON));

        ClaimedTask task = api.claim(List.of(Engine.CUSTOM)).orElseThrow();

        assertThat(task.taskId()).isEqualTo(7L);
        assertThat(task.engine()).isEqualTo(Engine.CUSTOM);
        assertThat(task.heartbeatIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void 가져갈_작업이_없으면_빈_값() {
        server.expect(requestTo("http://api.test/api/worker/tasks/claim")).andRespond(withNoContent());

        assertThat(api.claim(List.of(Engine.CUSTOM))).isEmpty();
    }

    @Test
    void 하트비트_응답으로_취소_요청을_읽는다() {
        server.expect(requestTo("http://api.test/api/worker/tasks/7/heartbeat"))
            .andExpect(content().json("{\"progress\":40}"))
            .andRespond(withSuccess("{\"cancelRequested\":true,\"leaseUntil\":\"2026-11-02T00:00:00Z\"}", MediaType.APPLICATION_JSON));

        assertThat(api.heartbeat(7L, 40)).isTrue();
    }

    @Test
    void 맡지_않은_작업이거나_지워진_작업이면_임대를_잃은_것으로_본다() {
        server.expect(requestTo("http://api.test/api/worker/tasks/7/complete")).andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://api.test/api/worker/tasks/8/heartbeat")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> api.complete(7L)).isInstanceOf(LeaseLostException.class);
        assertThatThrownBy(() -> api.heartbeat(8L, 0)).isInstanceOf(LeaseLostException.class);
    }
}
