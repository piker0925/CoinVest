package com.coinvest;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.TimeZone;

/**
 * 통합 테스트 공통 기반 클래스.
 * 1. 수동 싱글톤 패턴: 컨테이너 기동 및 생명주기를 완벽히 제어.
 * 2. 명시적 주입: @DynamicPropertySource를 사용하여 연결 정보 유실 차단.
 * 3. 안정성 보강: Redis Readiness 체크 (WaitingFor) 추가.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("coinvest_test")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort()); // 🚀 CI 안정성을 위한 Readiness 체크 추가

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
                redisTemplate.execute((RedisConnection connection) -> {
                    connection.serverCommands().flushAll();
                    return null;
                });
            } catch (Exception e) {
                String msg = "🚨 Redis Flush Failed! Reason: " + e.getMessage();
                System.err.println(msg);
                throw new RuntimeException(msg, e);
            }
        }
    }
}
