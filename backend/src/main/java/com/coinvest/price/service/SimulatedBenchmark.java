package com.coinvest.price.service;

import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.trading.service.MarketHoursService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo 모드 전용 가상 벤치마크 지수 생성기.
 * 평균 회귀(Mean Reversion) 모델을 적용하여 무한 발산을 방지함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coinvest.demo.enabled", matchIfMissing = true)
public class SimulatedBenchmark {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MarketHoursService marketHoursService;
    private final Random random = new Random();

    // 지수별 현재가 관리
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    
    // 지수 정의 (코드, 기준가, 회귀 속도)
    private static final List<BenchmarkSpec> INDICES = List.of(
            new BenchmarkSpec("KOSPI_SIM", new BigDecimal("2700.00"), 0.01),
            new BenchmarkSpec("SP500_SIM", new BigDecimal("5200.00"), 0.01)
    );

    @PostConstruct
    public void init() {
        for (BenchmarkSpec spec : INDICES) {
            currentPrices.put(spec.code(), spec.basePrice());
        }
    }

    /**
     * 1분마다 지수 변동 생성 및 Redis 저장 (ZSet 포함).
     */
    @Scheduled(fixedRate = 60000)
    public void generateBenchmarks() {
        // score = epochDay: LiveBenchmarkProvider와 동일한 스케일 사용 (통일)
        long today = LocalDate.now().toEpochDay();

        for (BenchmarkSpec spec : INDICES) {
            boolean isOpen = spec.code().contains("KOSPI") ?
                    marketHoursService.isKrxOpen() : marketHoursService.isNyseOpen();

            if (!isOpen) continue;

            BigDecimal lastPrice = currentPrices.get(spec.code());
            BigDecimal nextPrice = calculateNextPrice(spec, lastPrice);
            currentPrices.put(spec.code(), nextPrice);

            // 1. 실시간 현재가 저장
            String priceKey = RedisKeyConstants.getBenchmarkKey(PriceMode.DEMO, spec.code());
            redisTemplate.opsForValue().set(priceKey, nextPrice.toString(), Duration.ofMinutes(5));

            // 2. 이력 저장 (ZSet, score = epochDay): 동일 날짜 UPSERT
            String historyKey = RedisKeyConstants.getBenchmarkHistoryKey(PriceMode.DEMO, spec.code());
            redisTemplate.opsForZSet().removeRangeByScore(historyKey, today, today);
            redisTemplate.opsForZSet().add(historyKey, nextPrice.toString(), today);

            long ninetyDaysAgo = today - 90;
            redisTemplate.opsForZSet().removeRangeByScore(historyKey, 0, ninetyDaysAgo - 1);
        }
    }

    private BigDecimal calculateNextPrice(BenchmarkSpec spec, BigDecimal lastPrice) {
        BigDecimal gap = spec.basePrice().subtract(lastPrice);
        BigDecimal reversion = gap.multiply(BigDecimal.valueOf(spec.reversionSpeed()));

        double noisePercent = (random.nextDouble() * 0.004) - 0.002;
        BigDecimal noise = lastPrice.multiply(BigDecimal.valueOf(noisePercent));

        BigDecimal nextPrice = lastPrice.add(reversion).add(noise);
        
        return nextPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private record BenchmarkSpec(String code, BigDecimal basePrice, double reversionSpeed) {}
}
