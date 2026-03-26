package com.coinvest.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 설정.
 * Auditing을 활성화하여 BaseEntity의 createdAt, updatedAt을 자동 관리.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
