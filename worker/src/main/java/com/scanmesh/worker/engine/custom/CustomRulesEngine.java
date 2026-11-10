package com.scanmesh.worker.engine.custom;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.engine.EngineRunner;
import com.scanmesh.worker.engine.ResultStore;
import com.scanmesh.worker.engine.TaskContext;
import com.scanmesh.worker.engine.http.TargetFetcher;
import com.scanmesh.worker.engine.http.TargetFetcherFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 외부 도구 없이 직접 확인하는 규칙: 보안 헤더, 쿠키 설정, TLS, 민감 파일 노출.
 * 결과는 custom.jsonl에 한 줄에 하나씩 남긴다.
 */
@Slf4j
@Component
public class CustomRulesEngine implements EngineRunner {

    static final String RESULT_FILE = "custom.jsonl";
    private static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final TargetFetcherFactory fetchers;
    private final ResultStore results;
    private final Clock clock;
    /** 민감 파일 요청 사이의 간격. 대상 서버에 요청을 몰아 보내지 않는다 */
    private final Duration requestDelay;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public CustomRulesEngine(TargetFetcherFactory fetchers, ResultStore results) {
        this(fetchers, results, Clock.systemUTC(), Duration.ofMillis(200));
    }

    CustomRulesEngine(TargetFetcherFactory fetchers, ResultStore results, Clock clock, Duration requestDelay) {
        this.fetchers = fetchers;
        this.results = results;
        this.clock = clock;
        this.requestDelay = requestDelay;
    }

    @Override
    public Engine engine() {
        return Engine.CUSTOM;
    }

    @Override
    public void run(ClaimedTask task, TaskContext context) throws IOException, InterruptedException {
        List<Finding> findings = new ArrayList<>();
        try (TargetFetcher fetcher = fetchers.create(task.host(), context.pinnedAddress().orElse(null))) {
            URI start = URI.create(task.targetUrl());
            try {
                Page page = fetchFollowingRedirects(fetcher, start);
                findings.addAll(ResponseRules.evaluate(page.url(), page.response(), clock.instant()));
            } catch (SSLException e) {
                // 인증서 문제는 이 엔진이 찾으려던 결과다. 파일 검사는 같은 연결을 쓰므로 하지 않는다
                findings.add(new Finding("TLS_CERT_INVALID", "TLS 연결 실패(인증서 오류 등)", "HIGH", 295, start.toString(), e.getMessage()));
                write(task, findings);
                return;
            }
            context.reportProgress(20);
            checkSensitiveFiles(fetcher, start, context, findings);
        }
        write(task, findings);
        log.info("자체 규칙 완료: task={}, 발견 {}건", task.taskId(), findings.size());
    }

    private Page fetchFollowingRedirects(TargetFetcher fetcher, URI start) throws IOException {
        URI current = start;
        for (int redirects = 0; ; redirects++) {
            TargetFetcher.Response response = fetcher.get(current);
            String location = response.firstHeader("location");
            if (!REDIRECT_STATUSES.contains(response.status()) || location == null || redirects >= MAX_REDIRECTS) {
                return new Page(current, response);
            }
            URI next = resolve(current, location);
            // 다른 호스트로 가면 점검 대상이 아니므로 리다이렉트 응답까지만 본다
            if (next == null || !current.getHost().equalsIgnoreCase(next.getHost())) {
                return new Page(current, response);
            }
            current = next;
        }
    }

    private static URI resolve(URI base, String location) {
        try {
            return base.resolve(location.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void checkSensitiveFiles(TargetFetcher fetcher, URI start, TaskContext context, List<Finding> findings) throws InterruptedException {
        List<SensitiveFile> files = SensitiveFile.ALL;
        for (int i = 0; i < files.size(); i++) {
            SensitiveFile file = files.get(i);
            URI url = start.resolve(file.path());
            try {
                TargetFetcher.Response response = fetcher.get(url);
                if (response.status() == 200 && file.matches(response.body())) {
                    // 파일 내용은 비밀 값이므로 결과에 옮기지 않는다
                    findings.add(new Finding("SENSITIVE_FILE", file.title(), file.severity(), file.cweId(), url.toString(), "응답 내용이 해당 파일 형식과 일치합니다."));
                }
            } catch (IOException e) {
                log.info("민감 파일 확인 실패: {}, {}", url, e.toString());
            }
            context.reportProgress(20 + (i + 1) * 80 / files.size());
            Thread.sleep(requestDelay);
        }
    }

    private void write(ClaimedTask task, List<Finding> findings) throws IOException {
        Path file = results.directoryFor(task).resolve(RESULT_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            for (Finding finding : findings) {
                writer.write(json.writeValueAsString(finding));
                writer.newLine();
            }
        }
    }

    private record Page(URI url, TargetFetcher.Response response) {
    }
}
