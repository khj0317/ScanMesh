package com.scanmesh.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 시간을 주입받아 쓰면 테스트에서 만료 시각 같은 것을 원하는 대로 바꿀 수 있다 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
