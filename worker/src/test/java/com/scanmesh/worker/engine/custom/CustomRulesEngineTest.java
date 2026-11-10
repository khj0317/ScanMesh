package com.scanmesh.worker.engine.custom;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.engine.EngineTestSupport;
import com.scanmesh.worker.engine.EngineTestSupport.FakeContext;
import com.scanmesh.worker.engine.EngineTestSupport.FakeFetchers;
import com.scanmesh.worker.engine.ResultStore;
import com.scanmesh.worker.engine.http.TargetFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRulesEngineTest {

    @TempDir
    Path results;

    @Test
    void 고정한_IP로_연결하고_민감_파일을_찾아_결과_파일에_남긴다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/.env") ? EngineTestSupport.ok("DB_PASSWORD=hunter2") : EngineTestSupport.ok("<html>home</html>"));
        FakeContext context = new FakeContext("93.184.215.14");

        String output = run(fetchers, context);

        assertThat(fetchers.created).containsExactly("example.com@93.184.215.14");
        assertThat(output).contains("SENSITIVE_FILE", "https://example.com/.env", "MISSING_CSP");
        assertThat(output).doesNotContain("hunter2");
        assertThat(context.progress).isEqualTo(100);
    }

    @Test
    void 같은_호스트_리다이렉트는_따라가고_최종_응답을_검사한다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/")
            ? new TargetFetcher.Response(302, Map.of("location", List.of("/home")), "", null)
            : EngineTestSupport.ok("<html></html>"));

        String output = run(fetchers, new FakeContext(null));

        assertThat(fetchers.requested).startsWith("https://example.com/", "https://example.com/home");
        assertThat(output).contains("\"url\":\"https://example.com/home\"");
    }

    @Test
    void 다른_호스트로_보내는_리다이렉트는_따라가지_않는다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers((Function<URI, TargetFetcher.Response>) uri -> uri.getPath().equals("/")
            ? new TargetFetcher.Response(302, Map.of("location", List.of("http://169.254.169.254/latest/meta-data")), "", null)
            : EngineTestSupport.ok(""));

        run(fetchers, new FakeContext(null));

        assertThat(fetchers.requested).noneMatch(url -> url.contains("169.254.169.254"));
    }

    @Test
    void 민감_파일_요청이_실패해도_나머지를_계속_확인한다() throws Exception {
        FakeFetchers fetchers = new FakeFetchers(uri -> uri.getPath().equals("/.env") ? null : EngineTestSupport.ok("<html></html>"));

        String output = run(fetchers, new FakeContext(null));

        assertThat(fetchers.requested).contains("https://example.com/.git/HEAD");
        assertThat(output).contains("MISSING_CSP");
    }

    private String run(FakeFetchers fetchers, FakeContext context) throws Exception {
        ResultStore store = new ResultStore(EngineTestSupport.properties(results, Duration.ofSeconds(15), 4));
        CustomRulesEngine engine = new CustomRulesEngine(fetchers, store, Clock.systemUTC(), Duration.ZERO);
        var task = EngineTestSupport.task(Engine.CUSTOM);

        engine.run(task, context);

        return Files.readString(store.directoryFor(task).resolve(CustomRulesEngine.RESULT_FILE));
    }
}
