package com.secuscan.api.auth;

import com.secuscan.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends IntegrationTest {

    @Test
    void 가입하고_로그인한_토큰으로_내_정보를_본다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String accessToken = field(login(email), "accessToken");

        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void 대소문자와_공백만_다른_이메일로는_다시_가입할_수_없다() throws Exception {
        String email = uniqueEmail();
        signup(email);

        postJson("/api/auth/signup", """
            {"email":"  %s ","password":"%s","name":"테스터"}
            """.formatted(email.toUpperCase(), PASSWORD))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void 약한_비밀번호와_잘못된_이메일은_가입할_수_없다() throws Exception {
        postJson("/api/auth/signup", """
            {"email":"%s","password":"onlyletters","name":"테스터"}
            """.formatted(uniqueEmail()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        postJson("/api/auth/signup", """
            {"email":"not-an-email","password":"%s","name":"테스터"}
            """.formatted(PASSWORD))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_이메일과_틀린_비밀번호는_같은_응답으로_막는다() throws Exception {
        String email = uniqueEmail();
        signup(email);

        String wrongPassword = postJson("/api/auth/login", """
            {"email":"%s","password":"wrong12345"}
            """.formatted(email))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        String unknownEmail = postJson("/api/auth/login", """
            {"email":"%s","password":"%s"}
            """.formatted(uniqueEmail(), PASSWORD))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void 토큰이_없거나_위조됐으면_401() throws Exception {
        mockMvc.perform(get("/api/members/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String email = uniqueEmail();
        signup(email);
        String accessToken = field(login(email), "accessToken");
        String tampered = accessToken.substring(0, accessToken.length() - 2) + "xx";

        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 리프레시_토큰은_쓸_때마다_바뀐다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String first = field(login(email), "refreshToken");

        String refreshed = postJson("/api/auth/refresh", refreshBody(first))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = field(refreshed, "refreshToken");

        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + field(refreshed, "accessToken")))
            .andExpect(status().isOk());
    }

    @Test
    void 버린_리프레시_토큰이_다시_오면_탈취로_보고_모든_로그인을_끊는다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String stolen = field(login(email), "refreshToken");
        String current = field(postJson("/api/auth/refresh", refreshBody(stolen))
            .andReturn().getResponse().getContentAsString(), "refreshToken");

        // 훔친 사람이 이미 바뀐 토큰을 쓴다
        postJson("/api/auth/refresh", refreshBody(stolen))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 정상 사용자의 최신 토큰도 함께 끊겨서 다시 로그인해야 한다
        postJson("/api/auth/refresh", refreshBody(current))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_리프레시_토큰으로_동시에_요청해도_한_번만_성공한다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String token = field(login(email), "refreshToken");

        int threads = 5;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            calls.add(() -> {
                start.await();
                MvcResult result = postJson("/api/auth/refresh", refreshBody(token)).andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = calls.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses).filteredOn(status -> status != 200).allMatch(status -> status == 401);
    }

    @Test
    void 로그아웃한_리프레시_토큰은_더_쓸_수_없다() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String token = field(login(email), "refreshToken");

        postJson("/api/auth/logout", refreshBody(token))
            .andExpect(status().isNoContent());
        postJson("/api/auth/refresh", refreshBody(token))
            .andExpect(status().isUnauthorized());
    }

    private static String refreshBody(String token) {
        return """
            {"refreshToken":"%s"}
            """.formatted(token);
    }
}
