package com.coinvest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 공통 기반 클래스.
 */
@SpringBootTest
@ActiveProfiles("local")
public abstract class AbstractIntegrationTest {
}
