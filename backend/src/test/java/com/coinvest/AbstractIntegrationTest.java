package com.coinvest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 공통 기반 클래스.
 * Spring Boot 3.1+의 @ServiceConnection을 사용하여 도커 컨테이너와 Spring 빈을 완벽하게 자동 연결함.
 * 클래스 상속 구조에서 컨테이너가 단 한 번만 실행되도록 static 선언 및 Singleton 패턴 유지.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("coinvest_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // static 블록이나 manual start 없이 @Container + @Testcontainers 조합으로 
    // Spring Boot가 최적의 타이밍에 컨테이너를 관리하게 함.
}
