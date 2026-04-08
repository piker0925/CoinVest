package com.coinvest.price.service;

import com.coinvest.asset.domain.AssetClass;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.TickerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo 모드 전용 가상 가격 생성기.
 * Random Walk 모델을 사용하여 1초마다 가격 변동 이벤트 발행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coinvest.demo.enabled", matchIfMissing = true)
public class SimulatedPriceProvider {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();

    // 자산별 현재 가격 관리 (로컬 캐시)
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    
    // 자산 메타데이터 정의
    private static final List<SimulatedAssetSpec> ASSETS = List.of(
            new SimulatedAssetSpec("CRYPTO:VTC", AssetClass.CRYPTO, Currency.KRW, new BigDecimal("60000000")),
            new SimulatedAssetSpec("US_STOCK:PINE", AssetClass.US_STOCK, Currency.USD, new BigDecimal("150")),
            new SimulatedAssetSpec("KR_STOCK:SSEN", AssetClass.KR_STOCK, Currency.KRW, new BigDecimal("75000")),
            new SimulatedAssetSpec("US_STOCK:TCHN", AssetClass.US_STOCK, Currency.USD, new BigDecimal("200")),
            new SimulatedAssetSpec("KR_STOCK:LUNE", AssetClass.KR_STOCK, Currency.KRW, new BigDecimal("45000")),
            new SimulatedAssetSpec("US_STOCK:PHNX", AssetClass.US_STOCK, Currency.USD, new BigDecimal("50")),
            new SimulatedAssetSpec("CRYPTO:NEON", AssetClass.CRYPTO, Currency.KRW, new BigDecimal("12000")),
            new SimulatedAssetSpec("CRYPTO:ATOM", AssetClass.CRYPTO, Currency.KRW, new BigDecimal("35000")),
            new SimulatedAssetSpec("US_STOCK:NOVA", AssetClass.US_STOCK, Currency.USD, new BigDecimal("80")),
            new SimulatedAssetSpec("US_STOCK:ORBN", AssetClass.US_STOCK, Currency.USD, new BigDecimal("120"))
    );

    @PostConstruct
    public void init() {
        log.info("Initializing SimulatedPriceProvider with {} assets", ASSETS.size());
        for (SimulatedAssetSpec spec : ASSETS) {
            currentPrices.put(spec.universalCode(), spec.initialPrice());
        }
    }

    /**
     * 1초마다 가격 변동 생성 및 이벤트 발행.
     */
    @Scheduled(fixedRate = 1000)
    public void generatePrices() {
        for (SimulatedAssetSpec spec : ASSETS) {
            BigDecimal lastPrice = currentPrices.get(spec.universalCode());
            BigDecimal nextPrice = calculateNextPrice(lastPrice);
            currentPrices.put(spec.universalCode(), nextPrice);

            TickerEvent event = TickerEvent.builder()
                    .universalCode(spec.universalCode())
                    .assetClass(spec.assetClass())
                    .quoteCurrency(spec.quoteCurrency())
                    .tradePrice(nextPrice)
                    .accTradePrice(BigDecimal.ZERO) // 시뮬레이션에서는 생략
                    .accTradeVolume(BigDecimal.ZERO)
                    .timestamp(System.currentTimeMillis())
                    .tradeTimestamp(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli())
                    .build();

            // Demo 채널로 발행
            redisTemplate.convertAndSend(RedisKeyConstants.getPriceTickerChannel(PriceMode.DEMO), event);
        }
    }

    /**
     * Random Walk: ±0.5% 이내 변동
     */
    private BigDecimal calculateNextPrice(BigDecimal lastPrice) {
        double changePercent = (random.nextDouble() * 0.01) - 0.005; // -0.005 ~ 0.005
        BigDecimal change = lastPrice.multiply(BigDecimal.valueOf(changePercent));
        BigDecimal nextPrice = lastPrice.add(change);
        
        // 가격이 0 이하로 내려가지 않도록 방어
        if (nextPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return lastPrice.multiply(new BigDecimal("0.001")).setScale(8, RoundingMode.HALF_UP);
        }
        
        return nextPrice.setScale(8, RoundingMode.HALF_UP);
    }

    private record SimulatedAssetSpec(
            String universalCode,
            AssetClass assetClass,
            Currency quoteCurrency,
            BigDecimal initialPrice
    ) {}
}
