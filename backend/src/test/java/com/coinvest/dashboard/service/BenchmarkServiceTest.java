package com.coinvest.dashboard.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.fx.domain.Currency;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.service.PortfolioSnapshotService;
import com.coinvest.portfolio.service.PortfolioValuationService;
import com.coinvest.dashboard.dto.PerformanceResponse;
import com.coinvest.dashboard.dto.Period;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceTest {

    @InjectMocks
    private BenchmarkService benchmarkService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioValuationService valuationService;

    @Mock
    private PortfolioSnapshotService snapshotService;

    @Mock
    private BotPerformanceProvider botPerformanceProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private User testUser;
    private Portfolio testPortfolio;
    private final Long userId = 1L;
    private final Long portfolioId = 10L;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .email("test@example.com")
            .role(UserRole.USER)
            .build();
        ReflectionTestUtils.setField(testUser, "id", userId);

        testPortfolio = Portfolio.builder()
            .name("Test Portfolio")
            .user(testUser)
            .baseCurrency(Currency.KRW)
            .netContribution(new BigDecimal("1000000"))
            .build();
        ReflectionTestUtils.setField(testPortfolio, "id", portfolioId);
        ReflectionTestUtils.setField(testPortfolio, "createdAt", LocalDateTime.now());
    }

    @Test
    @DisplayName("기간별 수익률 계산 - 스냅샷이 있는 경우 (Modified Simple Return)")
    void getMyPerformance_withSnapshot() {
        // given
        Period period = Period.ONE_MONTH;
        PortfolioValuation valuation = PortfolioValuation.builder()
            .portfolioId(portfolioId)
            .totalEvaluationBase(new BigDecimal("1200000"))
            .buyingPowerBase(new BigDecimal("50000"))
            .netContribution(new BigDecimal("1100000"))
            .baseCurrency(Currency.KRW)
            .isStaleExchangeRate(false)
            .build();

        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(testPortfolio));
        given(valuationService.evaluate(portfolioId)).willReturn(valuation);

        PortfolioSnapshot startSnapshot = PortfolioSnapshot.builder()
            .totalEvaluationBase(new BigDecimal("1000000"))
            .netContribution(new BigDecimal("1000000"))
            .build();

        given(snapshotService.getClosestSnapshotBefore(eq(portfolioId), any(LocalDate.class)))
            .willReturn(Optional.of(startSnapshot));

        // 🚀 botPerformanceProvider가 null을 반환하지 않도록 설정 (NPE 방지)
        given(botPerformanceProvider.getReturns(any(), any())).willReturn(Collections.emptyList());

        // when
        PerformanceResponse response = benchmarkService.getMyPerformance(portfolioId, userId, period);

        // then
        assertThat(response.getReturnRate()).isEqualByComparingTo("15.00");
    }
}
