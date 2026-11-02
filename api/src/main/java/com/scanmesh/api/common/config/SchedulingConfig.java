package com.scanmesh.api.common.config;

import com.scanmesh.api.scan.ScanProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ScanProperties.class)
public class SchedulingConfig {
}
