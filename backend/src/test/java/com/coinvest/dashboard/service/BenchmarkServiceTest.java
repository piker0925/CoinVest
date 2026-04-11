package com.coinvest.dashboard.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.repository.PortfolioSnapshotRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioValuationService valuationService;

    @Mock
    private BotPerformanceProvider botPerformanceProvider;

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
        // ID 강제 주입 (Reflection)
        ReflectionTestUtils.setField(testUser, "id", userId);

        testPortfolio = Portfolio.builder()
            .name("Test Portfolio")
            .user(testUser)
            .netContribution(new BigDecimal("1000000"))
            .build();
        ReflectionTestUtils.setField(testPortfolio, "id", portfolioId);
    }

    @Test
    @DisplayName("기간별 수익률 계산 - 스냅샷이 있는 경우 (Modified Simple Return)")
    void getMyPerformance_withSnapshot() {
        // given
        Period period = Period.ONE_MONTH;
        PortfolioValuation valuation = PortfolioValuation.builder()
            .totalEvaluationBase(new BigDecimal("1200000"))
            .buyingPowerBase(new BigDecimal("50000"))
            .netContribution(new BigDecimal("1100000")) // ncNow
            .build();

        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(testPortfolio));
        given(valuationService.evaluate(portfolioId)).willReturn(valuation);

        // 한 달 전 스냅샷 (V_start)
        PortfolioSnapshot startSnapshot = PortfolioSnapshot.builder()
            .totalEvaluationBase(new BigDecimal("1000000"))  // vStart
            .netContribution(new BigDecimal("1000000")) // ncStart
            .build();

        given(snapshotRepository.findClosestBefore(eq(portfolioId), any(LocalDateTime.class), any()))
            .willReturn(java.util.List.of(startSnapshot));

        // when
        PerformanceResponse response = benchmarkService.getMyPerformance(portfolioId, userId, period);

        // then
        assertThat(response.getReturnRate()).isEqualByComparingTo("15.00");
    }
}
