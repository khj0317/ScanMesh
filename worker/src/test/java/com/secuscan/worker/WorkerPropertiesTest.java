package com.secuscan.worker;

import com.secuscan.common.scan.Engine;
import com.secuscan.common.scan.WorkerKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"worker.kind=HEAVY", "worker.poll-interval=5s"})
class WorkerPropertiesTest {

    @Autowired
    private WorkerProperties properties;

    @Test
    void 워커_종류에_맞는_엔진만_맡는다() {
        assertThat(properties.kind()).isEqualTo(WorkerKind.HEAVY);
        assertThat(properties.engines()).containsExactly(Engine.ZAP);
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(5));
    }
}
