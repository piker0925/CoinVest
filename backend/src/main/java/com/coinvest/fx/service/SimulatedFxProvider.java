package com.coinvest.fx.service;

import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
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
import java.util.Random;

/**
 * Demo 모드 전용 가상 환율 생성기.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coinvest.demo.enabled", matchIfMissing = true)
public class SimulatedFxProvider {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();

    private static final BigDecimal BASE_RATE = new BigDecimal("1350.00");
    private BigDecimal currentRate = BASE_RATE;

    @PostConstruct
    public void init() {
        updateRedis();
    }

    /**
     * 1분마다 환율 변동 생성 및 Redis 갱신.
     */
    @Scheduled(fixedRate = 60000)
    public void generateFxRate() {
        double changePercent = (random.nextDouble() * 0.002) - 0.001; // -0.1% ~ 0.1%
        BigDecimal change = currentRate.multiply(BigDecimal.valueOf(changePercent));
        currentRate = currentRate.add(change).setScale(2, RoundingMode.HALF_UP);

        updateRedis();
    }

    private void updateRedis() {
        String key = RedisKeyConstants.getExchangeRateKey(PriceMode.DEMO, "USD:KRW");
        redisTemplate.opsForValue().set(key, currentRate.toString(), Duration.ofMinutes(5));
        
        if (log.isTraceEnabled()) {
            log.trace("Updated Demo FX rate: {} = {}", key, currentRate);
        }
    }
}
