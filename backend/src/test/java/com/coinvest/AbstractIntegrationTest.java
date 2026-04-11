package com.coinvest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 공통 기반 클래스.
 * 싱글톤 컨테이너 패턴을 사용하여 전체 테스트 실행 중 한 번만 인프라를 기동함.
 * 이를 통해 Spring 컨텍스트 재시작 시 발생하는 컨테이너 재기동 및 포트 충돌 문제를 원천 차단함.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES;
    private static final GenericContainer<?> REDIS;

    static {
        // PostgreSQL 컨테이너 설정 및 시작
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("coinvest_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // 컨테이너 재사용 최적화
        
        // Redis 컨테이너 설정 및 시작
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);

        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // DB 설정 주입
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        // Redis 설정 주입
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
