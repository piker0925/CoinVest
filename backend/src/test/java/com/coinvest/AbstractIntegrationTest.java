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
import org.testcontainers.utility.DockerImageName;

import java.util.TimeZone;

/**
 * 통합 테스트 공통 기반 클래스.
 * 생명주기 제어권을 뺏어가는 @Testcontainers, @Container, @ServiceConnection을 모두 제거하고,
 * 오직 수동 싱글톤 패턴(static block + @DynamicPropertySource)만 사용하여 
 * Spring 컨텍스트 재시작 시에도 도커 연결 무결성을 100% 보장함.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // 정적 싱글톤 컨테이너 선언
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("coinvest_test")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        // [잠재 리스크 해결] 서버 환경(UTC)에 상관없이 한국 시간 기준으로 테스트 수행
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        
        // JVM 기동 시 컨테이너를 단 한 번만 시작 (JUnit 확장에 의존하지 않음)
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Spring 컨텍스트가 로드될 때마다 컨테이너의 연결 정보를 수동으로 강제 주입.
     */
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
                // [정석] execute()를 사용하여 커넥션 누수를 원천 차단하고 명령을 안전하게 수행
                redisTemplate.execute((RedisConnection connection) -> {
                    connection.serverCommands().flushAll();
                    return null;
                });
            } catch (Exception e) {
                String msg = "🚨 Redis Flush Failed! Critical for test isolation. Reason: " + e.getMessage();
                System.err.println(msg);
                throw new RuntimeException(msg, e);
            }
        }
    }
}
