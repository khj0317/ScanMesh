package com.scanmesh.worker.engine.custom;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.finding.Severity;
import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.engine.EngineTestSupport;
import com.scanmesh.worker.engine.EngineTestSupport.FakeContext;
import com.scanmesh.worker.engine.EngineTestSupport.FakeFetchers;
import com.scanmesh.worker.engine.http.TargetFetcher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRulesEngineTest {

    @Test
    void 고정한_IP로_연결하고_민감_파일을_찾는다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/.env") ? EngineTestSupport.ok("DB_PASSWORD=hunter2") : EngineTestSupport.ok("<html>home</html>"));
        FakeContext context = new FakeContext("93.184.215.14");

        List<Finding> findings = run(fetchers, context);

        assertThat(fetchers.created).containsExactly("example.com@93.184.215.14");
        assertThat(findings).extracting(Finding::ruleId).contains("SENSITIVE_FILE", "MISSING_CSP");
        Finding env = findings.stream().filter(finding -> finding.ruleId().equals("SENSITIVE_FILE")).findFirst().orElseThrow();
        assertThat(env.url()).isEqualTo("https://example.com/.env");
        assertThat(env.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(env.remediation()).isNotBlank();
        assertThat(findings).allSatisfy(finding -> assertThat(String.valueOf(finding.evidence())).doesNotContain("hunter2"));
        assertThat(context.progress).isEqualTo(100);
    }

    @Test
    void 같은_호스트_리다이렉트는_따라가고_최종_응답을_검사한다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/")
            ? new TargetFetcher.Response(302, Map.of("location", List.of("/home")), "", null)
            : EngineTestSupport.ok("<html></html>"));

        List<Finding> findings = run(fetchers, new FakeContext(null));

        assertThat(fetchers.requested).startsWith("https://example.com/", "https://example.com/home");
        assertThat(findings).filteredOn(finding -> finding.ruleId().equals("MISSING_CSP"))
            .extracting(Finding::url).containsExactly("https://example.com/home");
    }

    @Test
    void 다른_호스트로_보내는_리다이렉트는_따라가지_않는다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/")
            ? new TargetFetcher.Response(302, Map.of("location", List.of("http://169.254.169.254/latest/meta-data")), "", null)
            : EngineTestSupport.ok(""));

        run(fetchers, new FakeContext(null));

        assertThat(fetchers.requested).noneMatch(url -> url.contains("169.254.169.254"));
    }

    @Test
    void 민감_파일_요청이_실패해도_나머지를_계속_확인한다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/.env") ? null : EngineTestSupport.ok("<html></html>"));

        List<Finding> findings = run(fetchers, new FakeContext(null));

        assertThat(fetchers.requested).contains("https://example.com/.git/HEAD");
        assertThat(findings).extracting(Finding::ruleId).contains("MISSING_CSP");
    }

    private static List<Finding> run(FakeFetchers fetchers, FakeContext context) throws Exception {
        return new CustomRulesEngine(fetchers, Clock.systemUTC(), Duration.ZERO).run(EngineTestSupport.task(Engine.CUSTOM), context);
    }
}
