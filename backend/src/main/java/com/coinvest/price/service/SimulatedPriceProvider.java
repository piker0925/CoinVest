package com.coinvest.price.service;

import com.coinvest.asset.domain.AssetClass;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.TickerEvent;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo 모드 전용 가상 가격 생성기.
 * - 코인은 24/7, 주식은 개장 시간에만 변동 (Loophole 1 해결)
 * - 평균 회귀 모델 적용 (Loophole 4 해결)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coinvest.demo.enabled", matchIfMissing = true)
public class SimulatedPriceProvider {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MarketHoursService marketHoursService;
    private final Random random = new Random();

    // 자산별 현재 가격 관리
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    
    // 자산 메타데이터 및 평균 회귀 설정 (코드, 타입, 통화, 기준가, 회귀속도)
    private static final List<SimulatedAssetSpec> ASSETS = List.of(
            new SimulatedAssetSpec("CRYPTO:VTC", AssetClass.CRYPTO, Currency.KRW, new BigDecimal("60000000"), 0.005),
            new SimulatedAssetSpec("CRYPTO:NEON", AssetClass.CRYPTO, Currency.KRW, new BigDecimal("12000"), 0.005),
            new SimulatedAssetSpec("CRYPTO:ZEN", AssetClass.CRYPTO, Currency.KRW, new BigDecimal("45000"), 0.005),
            new SimulatedAssetSpec("US_STOCK:PINE", AssetClass.US_STOCK, Currency.USD, new BigDecimal("150"), 0.01),
            new SimulatedAssetSpec("US_STOCK:TCHN", AssetClass.US_STOCK, Currency.USD, new BigDecimal("200"), 0.01),
            new SimulatedAssetSpec("KR_STOCK:SSEN", AssetClass.KR_STOCK, Currency.KRW, new BigDecimal("75000"), 0.01),
            new SimulatedAssetSpec("KR_STOCK:LUNE", AssetClass.KR_STOCK, Currency.KRW, new BigDecimal("45000"), 0.01)
    );

    @PostConstruct
    public void init() {
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
            // 장 시간 체크 - Loophole 1 해결
            if (!isMarketOpen(spec)) continue;

            BigDecimal lastPrice = currentPrices.get(spec.universalCode());
            BigDecimal nextPrice = calculateNextPrice(spec, lastPrice);
            currentPrices.put(spec.universalCode(), nextPrice);

            TickerEvent event = TickerEvent.builder()
                    .universalCode(spec.universalCode())
                    .assetClass(spec.assetClass())
                    .quoteCurrency(spec.quoteCurrency())
                    .tradePrice(nextPrice)
                    .accTradePrice(BigDecimal.ZERO)
                    .accTradeVolume(BigDecimal.ZERO)
                    .timestamp(System.currentTimeMillis())
                    .tradeTimestamp(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli())
                    .build();

            redisTemplate.convertAndSend(RedisKeyConstants.getPriceTickerChannel(PriceMode.DEMO), event);
        }
    }

    private boolean isMarketOpen(SimulatedAssetSpec spec) {
        if (spec.assetClass() == AssetClass.CRYPTO) return true;
        if (spec.assetClass() == AssetClass.KR_STOCK) return marketHoursService.isKrxOpen();
        if (spec.assetClass() == AssetClass.US_STOCK) return marketHoursService.isNyseOpen();
        return false;
    }

    /**
     * 평균 회귀 모델 적용 - Loophole 4 해결
     */
    private BigDecimal calculateNextPrice(SimulatedAssetSpec spec, BigDecimal lastPrice) {
        // 1. 평균 회귀: 기준가로 수렴하려는 힘
        BigDecimal gap = spec.initialPrice().subtract(lastPrice);
        BigDecimal reversion = gap.multiply(BigDecimal.valueOf(spec.reversionSpeed()));

        // 2. 랜덤 노이즈 (±0.1% 내외)
        double noisePercent = (random.nextDouble() * 0.002) - 0.001;
        BigDecimal noise = lastPrice.multiply(BigDecimal.valueOf(noisePercent));

        BigDecimal nextPrice = lastPrice.add(reversion).add(noise);
        
        // 0원 방어
        if (nextPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return spec.initialPrice().multiply(new BigDecimal("0.1"));
        }
        
        return nextPrice.setScale(8, RoundingMode.HALF_UP);
    }

    private record SimulatedAssetSpec(
            String universalCode,
            AssetClass assetClass,
            Currency quoteCurrency,
            BigDecimal initialPrice,
            double reversionSpeed
    ) {}
}
