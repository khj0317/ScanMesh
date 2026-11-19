package com.scanmesh.worker.engine.container;

import com.scanmesh.common.finding.Finding;
import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.egress.EgressProxy;
import com.scanmesh.worker.engine.EngineProperties;
import com.scanmesh.worker.engine.ScanFailure;
import com.scanmesh.worker.engine.EngineRunner;
import com.scanmesh.worker.engine.ResultStore;
import com.scanmesh.worker.engine.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** ZAP baseline으로 사이트를 둘러보며 응답을 살핀다. 공격 요청은 보내지 않는다. 원본 보고서는 zap.json으로 남긴다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZapEngine implements EngineRunner {

    private static final String REPORT = "zap.json";
    /** 0주차 측정: 탐색 2분 설정으로 50초. 사이트가 크면 탐색 시간을 다 쓴다 */
    private static final Duration EXPECTED = Duration.ofMinutes(3);
    /** zap-baseline.py의 종료 코드: 0 통과, 1 FAIL 항목 있음, 2 WARN 항목 있음, 3 그 밖의 오류 */
    private static final int ZAP_ERROR = 3;
    private static final int MAX_FINDINGS = 500;

    private final ContainerRunner containers;
    private final ZapImage zapImage;
    private final ResultStore results;
    private final EngineProperties properties;

    @Override
    public Engine engine() {
        return Engine.ZAP;
    }

    @Override
    public List<Finding> run(ClaimedTask task, TaskContext context) throws IOException, InterruptedException {
        Path directory = results.directoryFor(task);
        ContainerRunner.Spec spec = new ContainerRunner.Spec(
            // 확장 규칙을 미리 받아 둔 이미지. 점검 중에는 바깥과 끊겨 받을 수 없다
            zapImage.prepare(),
            List.of(directory + ":/zap/wrk"),
            List.of(
                "zap-baseline.py", "-t", task.targetUrl(), "-m", "2", "-I", "-J", REPORT,
                // 탐색 스레드를 줄여 대상 서버에 몰리는 요청을 제한하고, 모든 요청을 출구 프록시로 보낸다
                "-z", "-config spider.thread=2"
                    + " -config connection.proxyChain.enabled=true"
                    + " -config connection.proxyChain.hostName=" + EgressSandbox.proxyName(task)
                    + " -config connection.proxyChain.port=" + EgressProxy.PORT
            ),
            properties.zapTimeout(),
            EXPECTED,
            directory.resolve("zap.log")
        );
        int exitCode = containers.run(spec, task, context);
        // 취약점이 발견돼도 0이 아닌 코드로 끝나므로, 보고서가 만들어졌는지로 성공을 판단한다
        if (exitCode >= ZAP_ERROR || !Files.exists(directory.resolve(REPORT))) {
            // 오류 메시지는 사용자 화면에 보이므로 워커 PC의 파일 경로는 워커 로그에만 남긴다
            log.warn("ZAP 실패: task={}, 종료 코드 {}, 로그 {}", task.taskId(), exitCode, spec.logFile());
            throw new ScanFailure("ZAP이 보고서를 만들지 못했습니다(종료 코드 " + exitCode + "). 대상이 응답하지 않았거나 엔진 오류일 수 있습니다.");
        }
        List<Finding> findings = ZapReport.parse(directory.resolve(REPORT), MAX_FINDINGS);
        log.info("ZAP 완료: task={}, 발견 {}건, 결과 폴더={}", task.taskId(), findings.size(), directory);
        return findings;
    }
}
