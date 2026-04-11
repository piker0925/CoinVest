package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.PortfolioSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotServiceTest {

    @InjectMocks
    private PortfolioSnapshotService snapshotService;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioValuationService valuationService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private Portfolio portfolio;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        portfolio = Portfolio.builder()
                .id(1L)
                .user(user)
                .name("My Portfolio")
                .netContribution(new BigDecimal("1000000"))
                .build();
    }

    @Test
    @DisplayName("일별 스냅샷 생성 성공")
    void createDailySnapshot_success() {
        // given
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        PortfolioValuation valuation = PortfolioValuation.builder()
                .totalEvaluationBase(new BigDecimal("1100000"))
                .buyingPowerBase(new BigDecimal("50000"))
                .build();

        when(snapshotRepository.existsByPortfolioIdAndSnapshotDate(eq(1L), any(LocalDateTime.class))).thenReturn(false);
        when(valuationService.evaluate(1L)).thenReturn(valuation);
        when(redisTemplate.opsForZSet()).thenReturn(mock(ZSetOperations.class));

        // when
        snapshotService.createDailySnapshot(portfolio, today);

        // then
        verify(snapshotRepository, times(1)).save(any(PortfolioSnapshot.class));
    }

    @Test
    @DisplayName("이미 스냅샷이 존재하면 생성하지 않음")
    void createDailySnapshot_alreadyExists() {
        // given
        LocalDate today = LocalDate.now();
        when(snapshotRepository.existsByPortfolioIdAndSnapshotDate(eq(1L), any(LocalDateTime.class))).thenReturn(true);

        // when
        snapshotService.createDailySnapshot(portfolio, today);

        // then
        verify(snapshotRepository, never()).save(any());
        verify(valuationService, never()).evaluate(anyLong());
    }
}
