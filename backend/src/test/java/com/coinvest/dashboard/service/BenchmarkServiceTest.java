package com.coinvest.dashboard.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.dashboard.dto.BenchmarkComparison;
import com.coinvest.dashboard.dto.Period;
import com.coinvest.dashboard.dto.PerformanceResponse;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.service.PortfolioSnapshotService;
import com.coinvest.portfolio.service.PortfolioValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BenchmarkService 단위 테스트.
 * 수익률 공식(Modified Simple Return)의 수치 정확성에 초점.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private Portfolio demoPortfolio;
    private User demoUser;

    @BeforeEach
    void setUp() {
        demoUser = User.builder()
            .id(1L)
            .email("demo@test.com")
            .role(UserRole.USER) // → PriceMode.DEMO
            .build();

        demoPortfolio = Portfolio.builder()
            .id(1L)
            .name("내 포트폴리오")
            .initialInvestment(new BigDecimal("1000000"))
            .netContribution(new BigDecimal("1000000"))
            .baseCurrency(Currency.KRW)
            .priceMode(PriceMode.DEMO)
            .user(demoUser)
            .build();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // ─── 개인 수익률 테스트 ────────────────────────────────────────────────────

    @Test
    @DisplayName("should_calculateReturnRate_when_allPeriod")
    void should_calculateReturnRate_when_allPeriod() {
        // given: 원금 100만, 현재 130만 (순수익 30만)
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(demoPortfolio));
        when(valuationService.evaluate(1L)).thenReturn(buildValuation("1200000", "100000")); // 자산+현금=130만

        // when
        PerformanceResponse result = benchmarkService.getMyPerformance(1L, 1L, Period.ALL);

        // then: (1300000 - 1000000) / 1000000 × 100 = 30.00%
        assertThat(result.getReturnRate()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(result.getProfitLoss()).isEqualByComparingTo(new BigDecimal("300000.00"));
    }

    @Test
    @DisplayName("should_subtractContributionDelta_when_midPeriodDeposit")
    void should_subtractContributionDelta_when_midPeriodDeposit() {
        // 시나리오:
        // 1개월 전: 포트폴리오 100만, 순기여금 100만
        // 15일 전: 50만 추가 입금 (순기여금 → 150만)
        // 현재: 포트폴리오 155만, 순기여금 150만
        // 진짜 수익: 5만 (155 - 100 - 50 = 5만)
        // 1M 수익률: 5 / 100 × 100 = 5.00%

        Portfolio portfolio = Portfolio.builder()
            .id(1L)
            .name("입출금 포트폴리오")
            .initialInvestment(new BigDecimal("1000000"))
            .netContribution(new BigDecimal("1500000")) // 현재 순기여금 150만
            .baseCurrency(Currency.KRW)
            .priceMode(PriceMode.DEMO)
            .user(demoUser)
            .build();

        PortfolioSnapshot startSnapshot = PortfolioSnapshot.builder()
            .totalValueBase(new BigDecimal("1000000"))  // V_start = 100만
            .netContribution(new BigDecimal("1000000")) // NC_start = 100만
            .build();

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(valuationService.evaluate(1L))
            .thenReturn(buildValuation("1450000", "100000")); // 자산+현금 = 155만
        when(snapshotService.getClosestSnapshotBefore(eq(1L), any()))
            .thenReturn(Optional.of(startSnapshot));

        // when
        PerformanceResponse result = benchmarkService.getMyPerformance(1L, 1L, Period.ONE_MONTH);

        // then: (155 - 100 - (150 - 100)) / 100 × 100 = 5.00%
        assertThat(result.getReturnRate()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("should_fallbackToAllFormula_when_noSnapshotsForPeriod")
    void should_fallbackToAllFormula_when_noSnapshotsForPeriod() {
        // 초기 가동 구간 — 1M 스냅샷 없음
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(demoPortfolio));
        when(valuationService.evaluate(1L)).thenReturn(buildValuation("1100000", "50000"));
        when(snapshotService.getClosestSnapshotBefore(eq(1L), any())).thenReturn(Optional.empty());

        // when
        PerformanceResponse result = benchmarkService.getMyPerformance(1L, 1L, Period.ONE_MONTH);

        // then: ALL 공식 fallback — (115만 - 100만) / 100만 × 100 = 15.00%
        assertThat(result.getReturnRate()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("should_return404_when_notPortfolioOwner")
    void should_return404_when_notPortfolioOwner() {
        // 타인 포트폴리오 접근 시 IDOR 방어: 404
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(demoPortfolio));

        assertThatThrownBy(() ->
            benchmarkService.getMyPerformance(1L, 999L, Period.ALL) // 다른 userId
        ).isInstanceOf(BusinessException.class);
    }

    // ─── 벤치마크 비교 테스트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("should_compareBenchmarks_when_demoMode")
    void should_compareBenchmarks_when_demoMode() {
        // given
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(demoPortfolio));
        when(valuationService.evaluate(1L)).thenReturn(buildValuation("900000", "100000"));
        when(snapshotService.getClosestSnapshotBefore(any(), any())).thenReturn(Optional.empty());
        when(botPerformanceProvider.getReturns(any(), any())).thenReturn(Collections.emptyList());

        // ZSet 조회 — 시작값 2700, 현재값 2808 (4% 상승)
        ZSetOperations.TypedTuple<Object> startTuple = mock(ZSetOperations.TypedTuple.class);
        ZSetOperations.TypedTuple<Object> endTuple = mock(ZSetOperations.TypedTuple.class);
        when(startTuple.getValue()).thenReturn("2700.00");
        when(endTuple.getValue()).thenReturn("2808.00");
        when(zSetOperations.reverseRangeByScoreWithScores(anyString(), anyDouble(), anyDouble(),
            anyLong(), anyLong())).thenReturn(Set.of(startTuple));
        when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
            .thenReturn(Set.of(endTuple));

        // when
        BenchmarkComparison result = benchmarkService.compareBenchmarks(1L, 1L, Period.ONE_MONTH);

        // then
        assertThat(result.getBotReturns()).isEmpty(); // 6A 미구현 stub 확인
        assertThat(result.getIndexReturns()).isNotEmpty();
        assertThat(result.getPeriod()).isEqualTo("1M");
    }

    @Test
    @DisplayName("should_returnZeroReturn_when_noSnapshotsAndZeroNetContribution")
    void should_returnZeroReturn_when_noSnapshotsAndZeroNetContribution() {
        // 순기여금 0인 포트폴리오 (ZeroDivisionError 방어)
        Portfolio emptyPortfolio = Portfolio.builder()
            .id(1L).name("빈 포트폴리오")
            .initialInvestment(BigDecimal.ZERO)
            .netContribution(BigDecimal.ZERO)
            .baseCurrency(Currency.KRW)
            .priceMode(PriceMode.DEMO)
            .user(demoUser)
            .build();

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(emptyPortfolio));
        when(valuationService.evaluate(1L)).thenReturn(buildValuation("0", "0"));

        PerformanceResponse result = benchmarkService.getMyPerformance(1L, 1L, Period.ALL);

        assertThat(result.getReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private PortfolioValuation buildValuation(String assetValue, String cashValue) {
        return PortfolioValuation.builder()
            .portfolioId(1L)
            .totalEvaluationBase(new BigDecimal(assetValue))
            .buyingPowerBase(new BigDecimal(cashValue))
            .baseCurrency(Currency.KRW)
            .isStaleExchangeRate(false)
            .assetValuations(Collections.emptyList())
            .build();
    }
}
