package com.scanmesh.worker.engine.container;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** docker 명령을 프로세스로 실행한다. 셸을 거치지 않으므로 대상 주소가 명령으로 해석될 일이 없다 */
@Slf4j
class ProcessDocker implements ContainerRunner.Docker {

    @Override
    public Container run(List<String> command, Path logFile) throws IOException {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile())
            .start();
        return new Container() {
            @Override
            public boolean waitFor(Duration duration) throws InterruptedException {
                return process.waitFor(duration.toMillis(), TimeUnit.MILLISECONDS);
            }

            @Override
            public int exitCode() {
                return process.exitValue();
            }
        };
    }

    @Override
    public void remove(String name) {
        // 취소로 인터럽트된 스레드에서도 컨테이너는 지워야 하므로 표시를 지우고 기다린다
        boolean interrupted = Thread.interrupted();
        try {
            Process process = new ProcessBuilder("docker", "rm", "-f", name)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("컨테이너를 지우지 못했습니다: {}", name);
            }
        } catch (IOException e) {
            log.warn("컨테이너를 지우지 못했습니다: {}", name, e);
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
