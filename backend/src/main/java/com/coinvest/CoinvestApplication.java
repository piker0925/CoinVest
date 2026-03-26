package com.coinvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CoinVest: 암호화폐 포트폴리오 리밸런싱 엔진
 *
 * 실시간 가격 모니터링과 자동 리밸런싱 알림을 제공하는 Spring Boot 애플리케이션.
 *
 * 기술 스택:
 * - Java 21 (Virtual Thread)
 * - Spring Boot 3.4
 * - PostgreSQL + JPA
 * - Redis
 * - Kafka (KRaft 모드)
 *
 * 런타임 설정:
 * - GC: SerialGC (메모리 오버헤드 최소화)
 * - Heap: 256MB (t3.micro 타겟)
 * - Virtual Thread Pinning 모니터링: 활성화
 */
@SpringBootApplication
public class CoinvestApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoinvestApplication.class, args);
	}

}
