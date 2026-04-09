package com.coinvest.portfolio.service;

import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.PortfolioSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 포트폴리오 일별 가치 스냅샷 서비스.
 * 기간별 수익률(1M, 3M, ALL) 계산을 위한 시계열 데이터를 DB+Redis에 저장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioValuationService valuationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int CHUNK_SIZE = 100;
    private static final long SNAPSHOT_TTL_DAYS = 90;

    /**
     * 매일 23:00 KST 전체 포트폴리오 스냅샷 캡처.
     * Slice 기반 100건 청크 처리로 OOM/커넥션 고갈 방지.
     * saveAll() 배치 INSERT로 DB 부하 최소화.
     */
    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Seoul")
    public void captureDaily() {
        LocalDate today = LocalDate.now();
        log.info("Starting daily portfolio snapshot capture for date: {}", today);

        int pageNum = 0;
        int totalCaptured = 0;

        while (true) {
            Slice<Portfolio> slice = portfolioRepository.findAllBy(
                PageRequest.of(pageNum, CHUNK_SIZE)
            );

            List<PortfolioSnapshot> snapshots = new ArrayList<>();
            for (Portfolio portfolio : slice.getContent()) {
                try {
                    Optional<PortfolioSnapshot> snapshot = captureSnapshotInternal(portfolio, today);
                    snapshot.ifPresent(snapshots::add);
                } catch (Exception e) {
                    log.error("Failed to capture snapshot for portfolioId={}: {}",
                        portfolio.getId(), e.getMessage());
                }
            }

            if (!snapshots.isEmpty()) {
                snapshotRepository.saveAll(snapshots);
                snapshots.forEach(s -> cacheToRedis(s.getPortfolio(), s));
                totalCaptured += snapshots.size();
            }

            if (!slice.hasNext()) break;
            pageNum++;
        }

        log.info("Daily snapshot capture completed. Total: {}", totalCaptured);
    }

    /**
     * 단일 포트폴리오 스냅샷 캡처 (수동 호출 또는 테스트용).
     * 당일 스냅샷이 이미 존재하면 skip (멱등성).
     */
    @Transactional
    public Optional<PortfolioSnapshot> captureSnapshot(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return Optional.empty();

        Optional<PortfolioSnapshot> snapshot = captureSnapshotInternal(portfolio, LocalDate.now());
        snapshot.ifPresent(s -> {
            snapshotRepository.save(s);
            cacheToRedis(portfolio, s);
        });
        return snapshot;
    }

    /**
     * 특정 날짜의 스냅샷 조회.
     * Redis 우선 조회 → 미스 시 DB fallback.
     */
    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> getSnapshot(Long portfolioId, LocalDate date) {
        PriceMode mode = resolveMode(portfolioId);
        String redisKey = RedisKeyConstants.getPortfolioSnapshotHistoryKey(mode, portfolioId);

        // Redis ZSet: ZRANGEBYSCORE(epochDay, epochDay, LIMIT 1)
        Double score = (double) date.toEpochDay();
        var cached = redisTemplate.opsForZSet()
            .rangeByScoreWithScores(redisKey, score, score, 0, 1);

        if (cached != null && !cached.isEmpty()) {
            return parseSnapshotFromRedis(portfolioId, cached.iterator().next().getValue());
        }

        return snapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, date);
    }

    /**
     * 목표일 이전 가장 가까운 스냅샷 조회.
     * 주말/공휴일 조회 시 자동으로 가장 최근 영업일 스냅샷 반환 (Redis ZREVRANGEBYSCORE 시맨틱).
     * 스냅샷 자체가 없는 경우 empty 반환 (초기 가동 시 BenchmarkService가 netContribution fallback).
     */
    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> getClosestSnapshotBefore(Long portfolioId, LocalDate targetDate) {
        PriceMode mode = resolveMode(portfolioId);
        String redisKey = RedisKeyConstants.getPortfolioSnapshotHistoryKey(mode, portfolioId);

        double targetScore = targetDate.toEpochDay();
        var cached = redisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(redisKey, 0, targetScore, 0, 1);

        if (cached != null && !cached.isEmpty()) {
            return parseSnapshotFromRedis(portfolioId, cached.iterator().next().getValue());
        }

        // DB fallback
        List<PortfolioSnapshot> results = snapshotRepository.findClosestBefore(
            portfolioId, targetDate, PageRequest.of(0, 1)
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private Optional<PortfolioSnapshot> captureSnapshotInternal(Portfolio portfolio, LocalDate date) {
        // 멱등성: 당일 스냅샷이 이미 있으면 skip
        if (snapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolio.getId(), date)) {
            return Optional.empty();
        }

        PortfolioValuation valuation = valuationService.evaluate(portfolio.getId());
        if (valuation == null) return Optional.empty();

        PriceMode mode = PriceModeResolver.resolve(portfolio.getUser().getRole());

        // totalValue = 자산 평가액 + 현금(buyingPower)
        var totalValue = valuation.getTotalEvaluationBase()
            .add(valuation.getBuyingPowerBase());

        return Optional.of(PortfolioSnapshot.builder()
            .portfolio(portfolio)
            .snapshotDate(date)
            .totalValueBase(totalValue)
            .netContribution(portfolio.getNetContribution())
            .priceMode(mode)
            .build());
    }

    private void cacheToRedis(Portfolio portfolio, PortfolioSnapshot snapshot) {
        try {
            PriceMode mode = snapshot.getPriceMode();
            String redisKey = RedisKeyConstants.getPortfolioSnapshotHistoryKey(mode, portfolio.getId());

            String value = objectMapper.writeValueAsString(Map.of(
                "totalValueBase", snapshot.getTotalValueBase().toPlainString(),
                "netContribution", snapshot.getNetContribution().toPlainString()
            ));

            double score = snapshot.getSnapshotDate().toEpochDay();
            redisTemplate.opsForZSet().add(redisKey, value, score);

            // 90일 초과 데이터 삭제
            long cutoffDay = LocalDate.now().minusDays(SNAPSHOT_TTL_DAYS).toEpochDay();
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, cutoffDay - 1);

        } catch (JsonProcessingException e) {
            log.warn("Failed to cache snapshot to Redis for portfolioId={}: {}",
                portfolio.getId(), e.getMessage());
        }
    }

    private Optional<PortfolioSnapshot> parseSnapshotFromRedis(Long portfolioId, Object rawValue) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = objectMapper.readValue(rawValue.toString(), Map.class);
            // Redis 캐시 히트 시 경량 객체 반환 (portfolio 참조 없이 수익률 계산 가능)
            return Optional.of(PortfolioSnapshot.builder()
                .totalValueBase(new java.math.BigDecimal(map.get("totalValueBase")))
                .netContribution(new java.math.BigDecimal(map.get("netContribution")))
                .build());
        } catch (Exception e) {
            log.warn("Failed to parse snapshot from Redis for portfolioId={}", portfolioId);
            return Optional.empty();
        }
    }

    private PriceMode resolveMode(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
            .map(p -> PriceModeResolver.resolve(p.getUser().getRole()))
            .orElse(PriceMode.DEMO);
    }
}
