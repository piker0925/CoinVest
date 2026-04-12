package com.coinvest.dashboard.service;

import com.coinvest.dashboard.dto.BenchmarkComparison;
import com.coinvest.dashboard.dto.BenchmarkComparison.BotReturn;
import com.coinvest.dashboard.dto.BenchmarkComparison.IndexReturn;
import com.coinvest.dashboard.dto.Period;
import com.coinvest.dashboard.dto.PerformanceResponse;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.service.PortfolioSnapshotService;
import com.coinvest.portfolio.service.PortfolioValuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 개인화 벤치마크 대시보드 핵심 서비스.
 * 개인 수익률 계산 + 벤치마크 지수 비교 + 봇 수익률 비교.
 *
 * <p><b>수익률 공식 (Modified Simple Return — 중간 입출금 보정)</b>
 * <ul>
 *   <li>ALL: (V_now - NC_now) / NC_now × 100</li>
 *   <li>1M/3M: (V_now - V_start - (NC_now - NC_start)) / V_start × 100</li>
 * </ul>
 * NC_delta = NC_now - NC_start: 기간 중 순 입출금액. 이를 분자에서 빼야 진짜 투자 수익만 남음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenchmarkService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioValuationService valuationService;
    private final PortfolioSnapshotService snapshotService;
    private final BotPerformanceProvider botPerformanceProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    // Demo 지수 코드
    private static final Map<String, String> DEMO_INDEX_NAMES = Map.of(
        "KOSPI_SIM", "KOSPI (시뮬레이션)",
        "SP500_SIM", "S&P 500 (시뮬레이션)"
    );

    // Live 지수 코드
    private static final Map<String, String> LIVE_INDEX_NAMES = Map.of(
        "KOSPI",  "KOSPI",
        "SP500",  "S&P 500"
    );

    /**
     * 개인 수익률 및 자산 현황 조회.
     */
    public PerformanceResponse getMyPerformance(Long portfolioId, Long userId, Period period) {
        Portfolio portfolio = findPortfolioWithOwnerCheck(portfolioId, userId);
        PriceMode mode = PriceModeResolver.resolve(portfolio.getUser().getRole());

        PortfolioValuation valuation = valuationService.evaluate(portfolioId);
        if (valuation == null) {
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        BigDecimal totalValueNow = Optional.ofNullable(valuation.getTotalEvaluationBase()).orElse(BigDecimal.ZERO)
            .add(Optional.ofNullable(valuation.getBuyingPowerBase()).orElse(BigDecimal.ZERO));
        BigDecimal ncNow = Optional.ofNullable(portfolio.getNetContribution()).orElse(BigDecimal.ZERO);

        BigDecimal returnRate;
        if (period == Period.ALL) {
            // ALL: 순기여금 대비 현재 수익률
            returnRate = ncNow.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalValueNow.subtract(ncNow)
                    .divide(ncNow, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            // 1M / 3M: 과거 스냅샷 기반 Modified Simple Return
            // createdAt이 null인 경우 1년 전을 기본값으로 사용 (극단적 초기 상태 방어)
            LocalDate portfolioCreatedDate = portfolio.getCreatedAt() != null
                ? portfolio.getCreatedAt().toLocalDate()
                : LocalDate.now().minusYears(1);
            LocalDate startDate = period.getStartDate(portfolioCreatedDate);
            Optional<PortfolioSnapshot> startSnapshotOpt =
                snapshotService.getClosestSnapshotBefore(portfolioId, startDate);

            if (startSnapshotOpt.isEmpty()) {
                // 스냅샷 없으면 ALL 공식으로 fallback (초기 가동 구간)
                log.debug("No snapshot found for portfolioId={}, period={}. Falling back to ALL formula.",
                    portfolioId, period);
                returnRate = ncNow.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : totalValueNow.subtract(ncNow)
                        .divide(ncNow, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            } else {
                PortfolioSnapshot startSnapshot = startSnapshotOpt.get();
                BigDecimal vStart = Optional.ofNullable(startSnapshot.getTotalEvaluationBase()).orElse(BigDecimal.ZERO);
                BigDecimal ncStart = Optional.ofNullable(startSnapshot.getNetContribution()).orElse(BigDecimal.ZERO);

                if (vStart.compareTo(BigDecimal.ZERO) == 0) {
                    returnRate = BigDecimal.ZERO;
                } else {
                    // (V_now - V_start - (NC_now - NC_start)) / V_start × 100
                    BigDecimal ncDelta = ncNow.subtract(ncStart);
                    returnRate = totalValueNow.subtract(vStart).subtract(ncDelta)
                        .divide(vStart, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                }
            }
        }

        BigDecimal profitLoss = totalValueNow.subtract(ncNow);

        return PerformanceResponse.builder()
            .portfolioId(portfolioId)
            .portfolioName(portfolio.getName())
            .totalValue(totalValueNow.setScale(2, RoundingMode.HALF_UP))
            .netContribution(ncNow.setScale(2, RoundingMode.HALF_UP))
            .profitLoss(profitLoss.setScale(2, RoundingMode.HALF_UP))
            .returnRate(returnRate.setScale(2, RoundingMode.HALF_UP))
            .baseCurrency(portfolio.getBaseCurrency())
            .period(period.getCode())
            .staleExchangeRate(valuation.isStaleExchangeRate())
            .build();
    }

    /**
     * 벤치마크 비교 — 내 수익률 vs 지수 vs 봇 전략.
     */
    public BenchmarkComparison compareBenchmarks(Long portfolioId, Long userId, Period period) {
        Portfolio portfolio = findPortfolioWithOwnerCheck(portfolioId, userId);
        PriceMode mode = PriceModeResolver.resolve(portfolio.getUser().getRole());

        // 내 수익률
        PerformanceResponse myPerformance = getMyPerformance(portfolioId, userId, period);
        BigDecimal myReturn = myPerformance.getReturnRate();

        // 벤치마크 지수 수익률
        Map<String, String> indexNames = mode == PriceMode.DEMO ? DEMO_INDEX_NAMES : LIVE_INDEX_NAMES;
        LocalDate portfolioCreatedDate = portfolio.getCreatedAt() != null
            ? portfolio.getCreatedAt().toLocalDate()
            : LocalDate.now().minusYears(1);
        LocalDate startDate = period.getStartDate(portfolioCreatedDate);

        List<IndexReturn> indexReturns = new ArrayList<>();
        for (Map.Entry<String, String> entry : indexNames.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            try {
                IndexReturn indexReturn = calculateBenchmarkReturn(mode, code, name, startDate);
                indexReturns.add(indexReturn);
            } catch (Exception e) {
                log.warn("Failed to calculate benchmark return for code={}: {}", code, e.getMessage());
                indexReturns.add(IndexReturn.builder()
                    .code(code)
                    .name(name)
                    .returnRate(BigDecimal.ZERO)
                    .startValue(BigDecimal.ZERO)
                    .endValue(BigDecimal.ZERO)
                    .build());
            }
        }

        // 봇 수익률 (6A 미구현 시 빈 리스트)
        List<BotReturn> botReturns = botPerformanceProvider.getReturns(mode, period);

        return BenchmarkComparison.builder()
            .myReturn(myReturn)
            .indexReturns(indexReturns)
            .botReturns(botReturns)
            .period(period.getCode())
            .build();
    }

    // ─── 벤치마크 수익률 계산 ──────────────────────────────────────────────────

    /**
     * Redis ZSet에서 기간 시작/종료 지수 값을 조회하여 수익률 계산.
     * ZREVRANGEBYSCORE로 목표일 이전 가장 가까운 데이터를 조회
     * → 주말/공휴일에 조회해도 가장 최근 영업일 데이터 자동 fallback.
     */
    private IndexReturn calculateBenchmarkReturn(
            PriceMode mode, String code, String name, LocalDate startDate) {

        String historyKey = RedisKeyConstants.getBenchmarkHistoryKey(mode, code);

        BigDecimal startValue = getClosestBenchmarkValue(historyKey, startDate);
        BigDecimal endValue   = getLatestBenchmarkValue(historyKey);

        if (startValue == null || endValue == null || startValue.compareTo(BigDecimal.ZERO) == 0) {
            return IndexReturn.builder()
                .code(code).name(name)
                .returnRate(BigDecimal.ZERO)
                .startValue(startValue != null ? startValue : BigDecimal.ZERO)
                .endValue(endValue != null ? endValue : BigDecimal.ZERO)
                .build();
        }

        // (endValue - startValue) / startValue × 100
        BigDecimal returnRate = endValue.subtract(startValue)
            .divide(startValue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);

        return IndexReturn.builder()
            .code(code)
            .name(name)
            .returnRate(returnRate)
            .startValue(startValue)
            .endValue(endValue)
            .build();
    }

    /**
     * 목표일 이전 가장 가까운 벤치마크 값 조회 (ZREVRANGEBYSCORE).
     * Score = LocalDate.toEpochDay() (SimulatedBenchmark은 epochSecond 사용 — 추후 통일 필요)
     */
    private BigDecimal getClosestBenchmarkValue(String historyKey, LocalDate targetDate) {
        // SimulatedBenchmark는 score=epochSecond, LiveBenchmarkProvider는 score=epochDay.
        // Demo 모드에서는 epochSecond 기반으로 조회해야 함.
        // targetDate.atStartOfDay()의 epochSecond ~ targetDate.atTime(23,59,59)의 epochSecond 범위
        long dayStartEpochSec  = targetDate.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
        long dayEndEpochSec    = targetDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() - 1;

        // Demo (score=epochSecond): 목표일 이전 가장 가까운 값
        var result = redisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(historyKey, 0, dayEndEpochSec, 0, 1);

        if (result != null && !result.isEmpty()) {
            Object val = result.iterator().next().getValue();
            return parseBigDecimal(val);
        }
        return null;
    }

    /**
     * 최신 벤치마크 값 조회 (ZSet 최고 Score).
     */
    private BigDecimal getLatestBenchmarkValue(String historyKey) {
        var result = redisTemplate.opsForZSet()
            .reverseRangeWithScores(historyKey, 0, 0);

        if (result != null && !result.isEmpty()) {
            Object val = result.iterator().next().getValue();
            return parseBigDecimal(val);
        }
        return null;
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from Redis value: {}", value);
            return null;
        }
    }

    // ─── IDOR 방어 ───────────────────────────────────────────────────────────

    private Portfolio findPortfolioWithOwnerCheck(Long portfolioId, Long userId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (!portfolio.getUser().getId().equals(userId)) {
            // 존재를 숨겨 IDOR 방어
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }
        return portfolio;
    }
}
