package com.coinvest;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.TimeZone;

/**
 * 통합 테스트 공통 기반 클래스.
 * 1. 수동 싱글톤 패턴으로 도커 생명주기 관리.
 * 2. @DirtiesContext를 통해 테스트 클래스 간 컨텍스트 간섭 원천 차단 (Spring Boot 3.4 대응).
 * 3. 템플릿 패턴을 이용한 정석적인 데이터 격리 및 예외 처리.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("coinvest_test")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeEach
    void clearRedis() {
        if (redisTemplate != null) {
            try {
                // [정석] execute() 콜백을 사용하여 커넥션 누수를 방지하고 데이터를 초기화
                redisTemplate.execute((RedisConnection connection) -> {
                    connection.serverCommands().flushAll();
                    return null;
                });
            } catch (Exception e) {
                // 회피 금지: 데이터 오염 방지를 위해 실패 시 즉시 중단 및 상세 보고
                String msg = "🚨 Redis Flush Failed! Critical for test isolation. Reason: " + e.getMessage();
                System.err.println(msg);
                throw new RuntimeException(msg, e);
            }
        }
    }
}
