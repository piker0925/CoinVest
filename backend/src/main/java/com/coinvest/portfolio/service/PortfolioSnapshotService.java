package com.coinvest.portfolio.service;

import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.repository.PortfolioSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioValuationService valuationService;
    private final PortfolioRepository portfolioRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int SNAPSHOT_TTL_DAYS = 90;

    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> getSnapshot(Long portfolioId, LocalDate date) {
        return snapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, date.atStartOfDay());
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> getClosestSnapshotBefore(Long portfolioId, LocalDate date) {
        List<PortfolioSnapshot> results = snapshotRepository.findClosestBefore(
            portfolioId, date.atStartOfDay(), PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Transactional
    public void createDailySnapshot(Portfolio portfolio, LocalDate date) {
        if (snapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolio.getId(), date.atStartOfDay())) {
            log.debug("Snapshot already exists for portfolioId={} on {}",
                portfolio.getId(), date);
            return;
        }

        PortfolioValuation valuation = valuationService.evaluate(portfolio.getId());
        if (valuation == null) return;

        PriceMode mode = PriceModeResolver.resolve(portfolio.getUser().getRole());

        // totalValue = 자산 평가액 + 현금(buyingPower)
        var totalValue = valuation.getTotalEvaluationBase()
            .add(valuation.getBuyingPowerBase());

        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
            .portfolio(portfolio)
            .snapshotDate(date.atStartOfDay())
            .totalEvaluationBase(totalValue)
            .netContribution(portfolio.getNetContribution())
            .priceMode(mode)
            .build();

        snapshotRepository.save(snapshot);
        cacheToRedis(portfolio, snapshot);
        log.info("Portfolio snapshot created for portfolioId={}, date={}", portfolio.getId(), date);
    }

    private void cacheToRedis(Portfolio portfolio, PortfolioSnapshot snapshot) {
        try {
            PriceMode mode = snapshot.getPriceMode();
            String redisKey = RedisKeyConstants.getPortfolioSnapshotHistoryKey(mode, portfolio.getId());

            String value = objectMapper.writeValueAsString(Map.of(
                "totalValueBase", snapshot.getTotalEvaluationBase().toPlainString(),
                "netContribution", snapshot.getNetContribution().toPlainString()
            ));

            double score = snapshot.getSnapshotDate().toLocalDate().toEpochDay();
            redisTemplate.opsForZSet().add(redisKey, value, score);

            // 90일 초과 데이터 삭제
            long cutoffDay = LocalDate.now().minusDays(SNAPSHOT_TTL_DAYS).toEpochDay();
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, cutoffDay - 1);

        } catch (JsonProcessingException e) {
            log.warn("Failed to cache snapshot to Redis for portfolioId={}: {}",
                portfolio.getId(), e.getMessage());
        }
    }
}
