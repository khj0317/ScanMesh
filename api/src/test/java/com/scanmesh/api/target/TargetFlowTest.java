package com.scanmesh.api.target;

import com.jayway.jsonpath.JsonPath;
import com.scanmesh.api.common.net.BlockedAddressException;
import com.scanmesh.api.common.net.WebFetcher;
import com.scanmesh.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import static com.scanmesh.api.support.FakeNetworkConfiguration.HOSTS;
import static com.scanmesh.api.support.FakeNetworkConfiguration.TXT_RECORDS;
import static com.scanmesh.api.support.FakeNetworkConfiguration.WEB;
import static com.scanmesh.api.support.FakeNetworkConfiguration.address;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TargetFlowTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 주소를_정규화해서_등록하고_인증_방법을_알려준다() throws Exception {
        String token = memberToken();
        String host = uniqueHost();

        createTarget(token, "  HTTPS://" + host.toUpperCase() + ":443/shop?utm=1#top ", "DNS_TXT")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.baseUrl").value("https://" + host + "/shop"))
            .andExpect(jsonPath("$.host").value(host))
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.verification.method").value("DNS_TXT"))
            .andExpect(jsonPath("$.verification.token").value(containsString("scanmesh-")))
            .andExpect(jsonPath("$.verification.dnsRecordName").value("_scanmesh-verification." + host))
            .andExpect(jsonPath("$.verification.fileUrl").value("https://" + host + "/.well-known/scanmesh-verification.txt"));

        mockMvc.perform(get("/api/targets").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].host").value(host));
    }

    @Test
    void 같은_도메인은_두_번_등록할_수_없다() throws Exception {
        String token = memberToken();
        String host = uniqueHost();
        createTarget(token, "https://" + host, "DNS_TXT").andExpect(status().isCreated());

        createTarget(token, "http://" + host + "/other", "WELL_KNOWN_FILE")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_TARGET"));
    }

    @Test
    void IP_주소와_내부용_도메인은_주소_검사에서_막는다() throws Exception {
        String token = memberToken();
        for (String url : List.of(
            "http://127.0.0.1", "http://169.254.169.254/latest/meta-data", "http://[::1]", "http://2130706433",
            "http://localhost:8080", "http://intranet", "http://db.internal", "https://user:pw@" + uniqueHost(),
            "ftp://" + uniqueHost(), "file:///etc/passwd", "javascript:alert(1)"
        )) {
            createTarget(token, url, "DNS_TXT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_URL"));
        }
    }

    @Test
    void 내부망_주소를_가리키는_도메인은_등록할_수_없다() throws Exception {
        String token = memberToken();
        String host = uniqueHost();
        // 공인 주소와 내부 주소를 섞어 둬도 하나라도 내부 주소면 막는다
        HOSTS.put(host, new InetAddress[]{address("93.184.215.14"), address("10.0.0.5")});

        createTarget(token, "https://" + host, "DNS_TXT")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TARGET_ADDRESS_NOT_ALLOWED"));
    }

    @Test
    void 없는_도메인은_등록할_수_없다() throws Exception {
        createTarget(memberToken(), "https://nxdomain-" + uniqueHost(), "DNS_TXT")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TARGET_HOST_NOT_FOUND"));
    }

    @Test
    void DNS_TXT_레코드로_소유를_인증한다() throws Exception {
        String token = memberToken();
        String host = uniqueHost();
        String created = createTarget(token, "https://" + host, "DNS_TXT").andReturn().getResponse().getContentAsString();
        long id = idOf(created);
        String verificationToken = field(created, "verification.token");

        verify(token, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.detail").value(containsString("찾지 못했습니다")));

        // 확인 간격이 지나기 전에는 외부 조회를 하지 않는다
        verify(token, id)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("VERIFICATION_TOO_FREQUENT"));

        TXT_RECORDS.put("_scanmesh-verification." + host, List.of("v=spf1 -all", verificationToken));
        passCooldown(id);
        verify(token, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.target.verified").value(true))
            .andExpect(jsonPath("$.target.verifiedAt").exists());

        // 인증이 끝난 뒤에는 간격 제한 없이 결과만 돌려준다
        verify(token, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void 인증_파일로_소유를_인증한다() throws Exception {
        String token = memberToken();
        String host = uniqueHost();
        String created = createTarget(token, "http://" + host + ":8080/app", "WELL_KNOWN_FILE").andReturn().getResponse().getContentAsString();
        long id = idOf(created);
        String verificationToken = field(created, "verification.token");
        String fileUrl = "http://" + host + ":8080/.well-known/scanmesh-verification.txt";

        WEB.put(fileUrl, new WebFetcher.FetchResult(200, "something-else"));
        verify(token, id)
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.detail").value(containsString("인증 토큰과 다릅니다")));

        WEB.put(fileUrl, new BlockedAddressException(host, address("127.0.0.1")));
        passCooldown(id);
        verify(token, id)
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.detail").value(containsString("내부망")));

        WEB.put(fileUrl, new WebFetcher.FetchResult(200, "\n  " + verificationToken + "  \n"));
        passCooldown(id);
        verify(token, id)
            .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void 개발용_허용_호스트는_내부_주소여도_등록되고_바로_인증된다() throws Exception {
        String token = memberToken();
        HOSTS.put("juice-shop", new InetAddress[]{address("172.18.0.2")});

        String created = createTarget(token, "http://Juice-Shop:3000", "DNS_TXT")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.baseUrl").value("http://juice-shop:3000/"))
            .andExpect(jsonPath("$.verified").value(true))
            .andReturn().getResponse().getContentAsString();

        // 인증 확인을 눌러도 외부 조회 없이 인증된 상태를 돌려준다
        verify(token, idOf(created))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));
        createTarget(memberToken(), "http://host.docker.internal:3000", "WELL_KNOWN_FILE")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void 개발용_허용_호스트가_아니면_내부_이름은_그대로_막는다() throws Exception {
        String token = memberToken();
        for (String url : List.of("http://juice-shop.internal:3000", "http://other-app:3000", "http://172.18.0.2:3000")) {
            createTarget(token, url, "DNS_TXT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_URL"));
        }
    }

    @Test
    void 다른_회원의_대상은_보거나_인증하거나_지울_수_없다() throws Exception {
        String owner = memberToken();
        String other = memberToken();
        String created = createTarget(owner, "https://" + uniqueHost(), "DNS_TXT").andReturn().getResponse().getContentAsString();
        long id = idOf(created);

        mockMvc.perform(get("/api/targets").header("Authorization", bearer(other)))
            .andExpect(jsonPath("$", hasSize(0)));
        verify(other, id)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        mockMvc.perform(delete("/api/targets/" + id).header("Authorization", bearer(other)))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/targets/" + id).header("Authorization", bearer(owner)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/targets").header("Authorization", bearer(owner)))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 로그인하지_않으면_점검_대상_API를_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/api/targets"))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions createTarget(String token, String url, String method) throws Exception {
        return mockMvc.perform(post("/api/targets")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"url":"%s","verificationMethod":"%s"}
                """.formatted(url, method)));
    }

    private ResultActions verify(String token, long id) throws Exception {
        return mockMvc.perform(post("/api/targets/" + id + "/verify").header("Authorization", bearer(token)));
    }

    /** 확인 간격(10초)을 기다리지 않도록 마지막 시도 시각을 과거로 돌린다 */
    private void passCooldown(long id) {
        jdbcTemplate.update("update targets set last_verification_attempt_at = now() - interval '1 minute' where id = ?", id);
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String uniqueHost() {
        return "site-" + UUID.randomUUID().toString().substring(0, 8) + ".example.com";
    }
}
