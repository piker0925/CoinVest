package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.PortfolioSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PortfolioSnapshotService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioSnapshotServiceTest {

    @InjectMocks
    private PortfolioSnapshotService snapshotService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioValuationService valuationService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private Portfolio portfolio;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .id(1L)
            .email("test@example.com")
            .role(UserRole.USER)
            .build();

        portfolio = Portfolio.builder()
            .id(1L)
            .name("테스트 포트폴리오")
            .initialInvestment(new BigDecimal("1000000"))
            .netContribution(new BigDecimal("1000000"))
            .baseCurrency(Currency.KRW)
            .priceMode(PriceMode.DEMO)
            .user(user)
            .build();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("should_captureSnapshot_when_validPortfolioExists")
    void should_captureSnapshot_when_validPortfolioExists() {
        // given
        LocalDate today = LocalDate.now();
        PortfolioValuation valuation = PortfolioValuation.builder()
            .portfolioId(1L)
            .totalEvaluationBase(new BigDecimal("900000"))
            .buyingPowerBase(new BigDecimal("150000"))
            .baseCurrency(Currency.KRW)
            .isStaleExchangeRate(false)
            .assetValuations(Collections.emptyList())
            .build();

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(snapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, today)).thenReturn(false);
        when(valuationService.evaluate(1L)).thenReturn(valuation);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // when
        Optional<PortfolioSnapshot> result = snapshotService.captureSnapshot(1L);

        // then
        assertThat(result).isPresent();
        PortfolioSnapshot snapshot = result.get();
        assertThat(snapshot.getTotalValueBase())
            .isEqualByComparingTo(new BigDecimal("1050000")); // 900000 + 150000
        assertThat(snapshot.getNetContribution())
            .isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(snapshot.getPriceMode()).isEqualTo(PriceMode.DEMO);
    }

    @Test
    @DisplayName("should_skipCapture_when_snapshotAlreadyExistsToday")
    void should_skipCapture_when_snapshotAlreadyExistsToday() {
        // given
        LocalDate today = LocalDate.now();
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(snapshotRepository.existsByPortfolioIdAndSnapshotDate(1L, today)).thenReturn(true);

        // when
        Optional<PortfolioSnapshot> result = snapshotService.captureSnapshot(1L);

        // then — 멱등성: 중복 캡처 skip
        assertThat(result).isEmpty();
        verify(valuationService, never()).evaluate(any());
    }

    @Test
    @DisplayName("should_returnClosestSnapshot_when_exactDateMissing")
    void should_returnClosestSnapshot_when_exactDateMissing() {
        // given
        LocalDate target = LocalDate.now().minusDays(1); // 어제 (주말일 수 있음)
        LocalDate lastBusinessDay = target.minusDays(1); // 가장 가까운 영업일

        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
            .portfolio(portfolio)
            .snapshotDate(lastBusinessDay)
            .totalValueBase(new BigDecimal("1050000"))
            .netContribution(new BigDecimal("1000000"))
            .priceMode(PriceMode.DEMO)
            .build();

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        // Redis 미스
        when(zSetOperations.reverseRangeByScoreWithScores(anyString(), anyDouble(), anyDouble(),
            anyLong(), anyLong())).thenReturn(Collections.emptySet());
        // DB fallback
        when(snapshotRepository.findClosestBefore(eq(1L), eq(target), any()))
            .thenReturn(List.of(snapshot));

        // when
        Optional<PortfolioSnapshot> result = snapshotService.getClosestSnapshotBefore(1L, target);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getSnapshotDate()).isEqualTo(lastBusinessDay);
    }

    @Test
    @DisplayName("should_processInChunks_when_captureDaily")
    void should_processInChunks_when_captureDaily() {
        // given
        Portfolio p2 = Portfolio.builder()
            .id(2L).name("P2")
            .initialInvestment(BigDecimal.valueOf(500000))
            .netContribution(BigDecimal.valueOf(500000))
            .baseCurrency(Currency.KRW)
            .priceMode(PriceMode.DEMO)
            .user(user)
            .build();

        // 첫 번째 slice만 있고, 두 번째는 없음 (hasNext=false)
        var slice = new SliceImpl<>(List.of(portfolio, p2),
            PageRequest.of(0, 100), false);

        when(portfolioRepository.findAllBy(any())).thenReturn(slice);
        when(snapshotRepository.existsByPortfolioIdAndSnapshotDate(anyLong(), any()))
            .thenReturn(false);
        when(valuationService.evaluate(anyLong())).thenReturn(
            PortfolioValuation.builder()
                .totalEvaluationBase(BigDecimal.valueOf(800000))
                .buyingPowerBase(BigDecimal.valueOf(200000))
                .baseCurrency(Currency.KRW)
                .isStaleExchangeRate(false)
                .assetValuations(Collections.emptyList())
                .build()
        );
        when(snapshotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // when — 예외 없이 완료되어야 함
        snapshotService.captureDaily();

        // then — 2개 포트폴리오 saveAll 호출 확인
        verify(snapshotRepository, times(1)).saveAll(argThat(list ->
            ((List<?>) list).size() == 2
        ));
    }
}
