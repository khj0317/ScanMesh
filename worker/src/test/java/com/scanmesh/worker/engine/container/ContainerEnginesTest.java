package com.scanmesh.worker.engine.container;

import com.scanmesh.common.scan.Engine;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContainerEnginesTest {

    @TempDir
    Path results;

    private final FakeDocker docker = new FakeDocker();
    private final FakeFetchers healthy = new FakeFetchers(uri -> EngineTestSupport.ok(""));

    @Test
    void Nuclei는_속도를_제한하고_위험한_템플릿을_빼고_고정한_IP로_실행한다() throws Exception {
        docker.exitCode = 0;

        nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext("93.184.215.14"));

        assertThat(docker.command).containsSubsequence("docker", "run", "--rm", "--name", "scanmesh-task-7-2");
        assertThat(docker.command).containsSubsequence("--add-host", "example.com:93.184.215.14");
        assertThat(docker.command).containsSubsequence("nuclei:test", "-u", "https://example.com/");
        assertThat(docker.command).containsSubsequence("-rl", "50", "-c", "10");
        assertThat(docker.command).containsSubsequence("-etags", "dos,fuzz,intrusive,bruteforce");
        assertThat(docker.command).contains("templates-volume:/root/nuclei-templates");
        assertThat(docker.removed).isEmpty();
    }

    @Test
    void 개발용_대상은_IP를_고정하지_않는다() throws Exception {
        docker.exitCode = 0;

        nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null));

        assertThat(docker.command).doesNotContain("--add-host");
    }

    @Test
    void Nuclei가_실패하면_작업도_실패한다() {
        docker.exitCode = 1;

        assertThatThrownBy(() -> nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("종료 코드 1");
    }

    @Test
    void ZAP은_취약점을_찾아_0이_아닌_코드로_끝나도_보고서가_있으면_성공이다() throws Exception {
        docker.exitCode = 2;
        docker.onRun = () -> Files.writeString(store().directoryFor(EngineTestSupport.task(Engine.ZAP)).resolve("zap.json"), "{}");

        zap().run(EngineTestSupport.task(Engine.ZAP), new FakeContext(null));

        assertThat(docker.command).containsSubsequence("zap-baseline.py", "-t", "https://example.com/", "-m", "2", "-I", "-J", "zap.json");
    }

    @Test
    void ZAP_보고서가_없으면_실패다() {
        docker.exitCode = 2;

        assertThatThrownBy(() -> zap().run(EngineTestSupport.task(Engine.ZAP), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("보고서");
    }

    @Test
    void 대상이_연달아_응답하지_않으면_컨테이너를_지우고_실패한다() {
        docker.pollsUntilExit = Integer.MAX_VALUE;
        FakeFetchers dead = new FakeFetchers(uri -> null);
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ZERO, 3);

        assertThatThrownBy(() -> new NucleiEngine(new ContainerRunner(docker, properties, dead), store(), properties)
            .run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("응답하지 않아");
        assertThat(dead.requested).hasSize(3);
        assertThat(docker.removed).containsExactly("scanmesh-task-7-2");
    }

    @Test
    void 대상이_다시_응답하면_실패_횟수를_처음부터_센다() throws Exception {
        docker.pollsUntilExit = 6;
        AtomicInteger calls = new AtomicInteger();
        FakeFetchers flaky = new FakeFetchers(uri -> calls.incrementAndGet() % 2 == 0 ? EngineTestSupport.ok("") : null);
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ZERO, 2);
        docker.exitCode = 0;

        new NucleiEngine(new ContainerRunner(docker, properties, flaky), store(), properties)
            .run(EngineTestSupport.task(Engine.NUCLEI), new FakeContext(null));

        assertThat(docker.removed).isEmpty();
    }

    @Test
    void 취소되면_컨테이너를_지운다() {
        docker.pollsUntilExit = Integer.MAX_VALUE;
        FakeContext context = new FakeContext(null);
        context.cancelled = true;

        assertThatThrownBy(() -> nuclei(healthy).run(EngineTestSupport.task(Engine.NUCLEI), context))
            .isInstanceOf(InterruptedException.class);
        assertThat(docker.removed).containsExactly("scanmesh-task-7-2");
    }

    private NucleiEngine nuclei(FakeFetchers fetchers) {
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ofHours(1), 4);
        return new NucleiEngine(new ContainerRunner(docker, properties, fetchers), store(), properties);
    }

    private ZapEngine zap() {
        EngineProperties properties = EngineTestSupport.properties(results, Duration.ofHours(1), 4);
        return new ZapEngine(new ContainerRunner(docker, properties, healthy), store(), properties);
    }

    private ResultStore store() {
        return new ResultStore(EngineTestSupport.properties(results, Duration.ofHours(1), 4));
    }

    /** waitFor를 pollsUntilExit번 부른 뒤에 끝나는 가짜 컨테이너 */
    private static final class FakeDocker implements ContainerRunner.Docker {

        List<String> command;
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

        @FunctionalInterface
        interface Setup {
            void run() throws IOException;
        }
    }
}
