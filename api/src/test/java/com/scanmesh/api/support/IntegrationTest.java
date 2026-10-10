package com.scanmesh.api.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨테이너와 스프링 컨텍스트를 모든 테스트 클래스가 공유하므로,
 * 테스트끼리 데이터가 겹치지 않게 매번 고유한 이메일로 가입한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected void signup(String email) throws Exception {
        postJson("/api/auth/signup", """
            {"email":"%s","password":"%s","name":"테스터"}
            """.formatted(email, PASSWORD))
            .andExpect(status().isCreated());
    }

    /** 로그인해서 받은 응답 본문(JSON) */
    protected String login(String email) throws Exception {
        return postJson("/api/auth/login", """
            {"email":"%s","password":"%s"}
            """.formatted(email, PASSWORD))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    protected static String field(String json, String path) {
        return JsonPath.read(json, "$." + path);
    }
}
