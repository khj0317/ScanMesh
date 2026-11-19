package com.scanmesh.worker.engine.container;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.egress.EgressProxy;
import com.scanmesh.worker.engine.EngineProperties;
import com.scanmesh.worker.engine.EngineTestSupport;
import com.scanmesh.worker.engine.EngineTestSupport.FakeContext;
import com.scanmesh.worker.engine.EngineTestSupport.FakeFetchers;
import com.scanmesh.worker.engine.ResultStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContainerEnginesTest {

    @TempDir
    Path results;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

    private final FakeDocker docker = new FakeDocker();
    private final FakeFetchers healthy = new FakeFetchers(uri -> EngineTestSupport.ok(""));

    @Test
    void Nuclei는_속도를_제한하고_위험한_템플릿을_빼고_출구_프록시로만_실행한다() throws Exception {
        docker.exitCode = 0;

        nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext("93.184.215.14"));

        assertThat(docker.command).containsSubsequence("docker", "run", "--rm", "--name", "scanmesh-task-7-2");
        // 바깥으로 못 나가는 내부 네트워크에만 붙는다
        assertThat(docker.command).containsSubsequence("--network", "scanmesh-engines");
        assertThat(docker.command).containsSubsequence("--add-host", "example.com:93.184.215.14");
        assertThat(docker.command).containsSubsequence("nuclei:test", "-u", "https://example.com/");
        assertThat(docker.command).containsSubsequence("-rl", "50", "-c", "10");
        assertThat(docker.command).containsSubsequence("-etags", "dos,fuzz,intrusive,bruteforce");
        assertThat(docker.command).containsSubsequence("-proxy", "http://scanmesh-proxy-7-2:3128", "-ni");
        assertThat(docker.command).contains("templates-volume:/root/nuclei-templates");
        assertThat(docker.removed).containsExactly("scanmesh-proxy-7-2");
    }

    @Test
    void 작업마다_대상_호스트만_허용하는_출구_프록시를_띄운다() throws Exception {
        docker.exitCode = 0;

        nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext("93.184.215.14"));

        assertThat(docker.execs).anySatisfy(command -> assertThat(command).containsSubsequence("docker", "network", "create", "--internal", "scanmesh-engines"));
        assertThat(docker.execs).anySatisfy(command -> assertThat(command).containsSubsequence("docker", "build", "-t"));
        assertThat(docker.execs).anySatisfy(command -> assertThat(command)
            .containsSubsequence("docker", "run", "-d", "--rm", "--name", "scanmesh-proxy-7-2", "--network", "bridge")
            .endsWith("example.com", "443", "93.184.215.14"));
        assertThat(docker.execs).anySatisfy(command -> assertThat(command).containsExactly("docker", "network", "connect", "scanmesh-engines", "scanmesh-proxy-7-2"));
    }

    @Test
    void 개발용_대상은_IP를_고정하지_않고_프록시가_이름으로_찾는다() throws Exception {
        docker.exitCode = 0;

        nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null));

        // 엔진 안에서는 갈 곳 없는 주소로 풀어 두고, 실제 연결은 프록시가 한다
        assertThat(docker.command).containsSubsequence("--add-host", "example.com:192.0.2.1");
        assertThat(docker.execs).anySatisfy(command -> assertThat(command).endsWith("example.com", "443", "-"));
    }

    @Test
    void 출구_프록시가_준비되지_않으면_엔진을_띄우지_않는다() {
        docker.proxyReady = false;
        docker.proxyStartExitCode = 125;

        assertThatThrownBy(() -> nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("출구 프록시 시작");
        assertThat(docker.command).isNull();
    }

    @Test
    void Nuclei가_실패하면_작업도_실패한다() {
        docker.exitCode = 1;

        assertThatThrownBy(() -> nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("종료 코드 1")
            .hasMessageNotContaining(results.toString());
    }

    @Test
    void ZAP은_취약점을_찾아_0이_아닌_코드로_끝나도_보고서가_있으면_성공이다() throws Exception {
        docker.exitCode = 2;
        docker.onRun = () -> Files.writeString(store().directoryFor(EngineTestSupport.task(Engine.ZAP)).resolve("zap.json"), "{}");

        zap().run(EngineTestSupport.task(Engine.ZAP), new FakeContext(null));

        assertThat(docker.command).containsSubsequence("zap-baseline.py", "-t", "https://example.com/", "-m", "2", "-I", "-J", "zap.json");
        assertThat(String.join(" ", docker.command)).contains(
            "connection.proxyChain.enabled=true", "connection.proxyChain.hostName=scanmesh-proxy-7-2", "connection.proxyChain.port=3128");
        // 확장 규칙은 점검 전에 일반 네트워크에서 받아 이번 주 이미지로 저장해 두고, 점검은 그 이미지로 한다
        assertThat(docker.execs).anySatisfy(command -> assertThat(command)
            .containsSubsequence("docker", "run", "--name").containsSubsequence("zap:test", "zap.sh", "-cmd", "-addonupdate", "-addoninstall", "pscanrulesBeta"));
        assertThat(docker.execs).anySatisfy(command -> assertThat(command).startsWith("docker", "commit").endsWith("scanmesh-zap:2026-w47"));
        assertThat(docker.command).contains("scanmesh-zap:2026-w47");
        // 지난주 이미지는 지운다
        assertThat(docker.execs).anySatisfy(command -> assertThat(command).containsExactly("docker", "rmi", "scanmesh-zap:2026-w46"));
        assertThat(docker.execs).noneSatisfy(command -> assertThat(command).containsExactly("docker", "rmi", "scanmesh-zap:2026-w47"));
    }

    @Test
    void ZAP_보고서가_없으면_실패다() {
        docker.exitCode = 2;

        assertThatThrownBy(() -> zap().run(EngineTestSupport.task(Engine.ZAP), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("보고서")
            // 사용자 화면에 보이는 메시지에 워커 PC의 경로가 들어가지 않는다
            .hasMessageNotContaining(results.toString());
    }

    @Test
    void 대상이_연달아_응답하지_않으면_컨테이너를_지우고_실패한다() {
        docker.pollsUntilExit = Integer.MAX_VALUE;
        FakeFetchers dead = new FakeFetchers(uri -> null);
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ZERO, 3);

        assertThatThrownBy(() -> new NucleiEngine(runner(properties, dead), store(), properties)
            .run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("응답하지 않아");
        assertThat(dead.requested).hasSize(3);
        assertThat(docker.removed).containsExactly("scanmesh-task-7-2", "scanmesh-proxy-7-2");
    }

    @Test
    void 대상이_다시_응답하면_실패_횟수를_처음부터_센다() throws Exception {
        docker.pollsUntilExit = 6;
        AtomicInteger calls = new AtomicInteger();
        FakeFetchers flaky = new FakeFetchers(uri -> calls.incrementAndGet() % 2 == 0 ? EngineTestSupport.ok("") : null);
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ZERO, 2);
        docker.exitCode = 0;

        new NucleiEngine(runner(properties, flaky), store(), properties)
            .run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null));

        assertThat(docker.removed).containsExactly("scanmesh-proxy-7-2");
    }

    @Test
    void 취소되면_컨테이너를_지운다() {
        docker.pollsUntilExit = Integer.MAX_VALUE;
        FakeContext context = new FakeContext(null);
        context.cancelled = true;

        assertThatThrownBy(() -> nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), context))
            .isInstanceOf(InterruptedException.class);
        assertThat(docker.removed).containsExactly("scanmesh-task-7-2", "scanmesh-proxy-7-2");
    }

    private NucleiEngine nuclei(FakeFetchers fetchers) {
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ofHours(1), 4);
        return new NucleiEngine(runner(properties, fetchers), store(), properties);
    }

    private ZapEngine zap() {
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ofHours(1), 4);
        return new ZapEngine(runner(properties, healthy), new ZapImage(docker, properties, CLOCK), store(), properties);
    }

    private ContainerRunner runner(EngineProperties properties, FakeFetchers fetchers) {
        return new ContainerRunner(docker, properties, fetchers, new EgressSandbox(docker, properties));
    }

    private ResultStore store() {
        return new ResultStore(EngineTestSupport.properties(results, Duration.ofHours(1), 4));
    }

    /**
     * waitFor를 pollsUntilExit번 부른 뒤에 끝나는 가짜 컨테이너.
     * 네트워크와 프록시 이미지는 처음에 없다고 답하고, 프록시는 바로 준비됐다고 답한다
     */
    private static final class FakeDocker implements ContainerRunner.Docker {

        List<String> command;
        final List<List<String>> execs = new ArrayList<>();
        boolean proxyReady = true;
        int proxyStartExitCode = 0;
        final List<String> removed = new ArrayList<>();
        int exitCode;
        int pollsUntilExit;
        Setup onRun = () -> { };

        @Override
        public Container run(List<String> command, Path logFile) throws IOException {
            this.command = command;
            onRun.run();
            AtomicInteger polls = new AtomicInteger();
            return new Container() {
                @Override
                public boolean waitFor(Duration duration) {
                    return polls.getAndIncrement() >= pollsUntilExit;
                }

                @Override
                public int exitCode() {
                    return exitCode;
                }
            };
        }

        @Override
        public void remove(String name) {
            removed.add(name);
        }

        @Override
        public ExecResult exec(List<String> command, Duration timeout) {
            execs.add(command);
            String joined = String.join(" ", command);
            if (joined.startsWith("docker network inspect") || joined.startsWith("docker image inspect")) {
                return new ExecResult(1, "No such object");
            }
            if (joined.startsWith("docker run -d")) {
                return new ExecResult(proxyStartExitCode, proxyStartExitCode == 0 ? "container-id" : "port is already allocated");
            }
            if (joined.startsWith("docker images scanmesh-zap")) {
                return new ExecResult(0, "scanmesh-zap:2026-w47\nscanmesh-zap:2026-w46\n");
            }
            if (joined.startsWith("docker logs")) {
                return new ExecResult(0, proxyReady ? EgressProxy.READY + " host=example.com" : "");
            }
            return new ExecResult(0, "");
        }

        @FunctionalInterface
        interface Setup {
            void run() throws IOException;
        }
    }
}
