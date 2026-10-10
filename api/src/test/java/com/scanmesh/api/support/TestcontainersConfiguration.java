package com.scanmesh.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 로컬 docker-compose, 배포(Supabase)와 같은 Postgres로 테스트해서 마이그레이션과
 * SELECT ... FOR UPDATE 락이 실제 DB에서 동작하는지 검증한다. (Docker가 켜져 있어야 한다)
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
