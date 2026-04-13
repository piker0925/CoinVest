package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.CandleData;
import com.coinvest.price.dto.TickerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 전역 가격 조회 서비스 (캐시 우선, 실패 시 Provider 라우팅).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceProviderRouter priceProviderRouter;
    private final AssetRepository assetRepository;

    public BigDecimal getCurrentPrice(String universalCode) {
        return getCurrentPrice(universalCode, PriceMode.LIVE);
    }

    /**
     * 현재가 조회.
     * 1. Redis 캐시 확인.
     * 2. 캐시 미스 시 적절한 Provider를 통해 실시간 조회 시도.
     */
    public BigDecimal getCurrentPrice(String universalCode, PriceMode mode) {
        String key = RedisKeyConstants.getTickerPriceKey(mode, universalCode);
        Object priceObj = redisTemplate.opsForValue().get(key);

        if (priceObj != null) {
            return new BigDecimal(priceObj.toString());
        }

        // 캐시 미스 시 실시간 조회 시도 (실시간 조회는 현재 LIVE에서만 지원하거나 모드에 따라 다를 수 있음)
        return fetchRealtimePrice(universalCode, mode).orElse(BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> getPrices(List<String> universalCodes) {
        return getPrices(universalCodes, PriceMode.LIVE);
    }

    /**
     * 다중 자산 가격 배치 조회 (성능 최적화).
     * Redis MGET 사용.
     */
    public Map<String, BigDecimal> getPrices(List<String> universalCodes, PriceMode mode) {
        if (universalCodes == null || universalCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = universalCodes.stream()
                .map(code -> RedisKeyConstants.getTickerPriceKey(mode, code))
                .collect(Collectors.toList());

        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        return IntStream.range(0, universalCodes.size())
                .boxed()
                .collect(Collectors.toMap(
                    universalCodes::get,
                    i -> {
                        Object val = values != null ? values.get(i) : null;
                        if (val != null) {
                            return new BigDecimal(val.toString());
                        }
                        // 캐시 미스 시 개별 조회
                        return getCurrentPrice(universalCodes.get(i), mode);
                    }
                ));
    }

    /**
     * 5분봉 캔들 데이터 조회.
     * Redis price window LIST (index 0 = 최신) 에서 close 가격을 읽어
     * OHLC를 합성(인접 close 기반)한 후 오래된 순서로 반환.
     */
    public List<CandleData> getCandles(String universalCode, PriceMode mode) {
        String windowKey = RedisKeyConstants.getPriceWindowKey(mode, universalCode);
        String slotKey = RedisKeyConstants.getPriceWindowSlotKey(mode, universalCode);

        List<Object> rawPrices = redisTemplate.opsForList().range(windowKey, 0, -1);
        Object currentSlotObj = redisTemplate.opsForValue().get(slotKey);

        if (rawPrices == null || rawPrices.isEmpty() || currentSlotObj == null) {
            return List.of();
        }

        long currentSlot = Long.parseLong(currentSlotObj.toString());
        long candleIntervalSec = 5 * 60L; // 5분 = 300초

        int size = rawPrices.size();
        List<CandleData> candles = new ArrayList<>(size);

        // rawPrices[0] = 최신(currentSlot), rawPrices[i] = currentSlot-i 슬롯
        // 오래된 것부터 순서대로 생성 (lightweight-charts는 시간 오름차순 필요)
        for (int i = size - 1; i >= 0; i--) {
            double close = Double.parseDouble(rawPrices.get(i).toString());
            long time = (currentSlot - i) * candleIntervalSec;

            // open = 이전 슬롯의 close (없으면 현재 close)
            double open = (i < size - 1)
                    ? Double.parseDouble(rawPrices.get(i + 1).toString())
                    : close;

            double high = Math.max(open, close);
            double low = Math.min(open, close);

            candles.add(new CandleData(time, open, high, low, close));
        }

        return candles;
    }

    private Optional<BigDecimal> fetchRealtimePrice(String universalCode, PriceMode mode) {
        // DEMO 모드일 경우 실시간 조회 생략 (SimulatedPriceProvider가 채워줄 것임)
        if (mode == PriceMode.DEMO) {
            return Optional.empty();
        }

        log.info("Price cache miss for {}. Fetching from provider...", universalCode);
        
        return assetRepository.findByUniversalCode(universalCode)
                .map(asset -> {
                    List<TickerEvent> events = priceProviderRouter.fetchPrices(List.of(asset));
                    if (!events.isEmpty()) {
                        BigDecimal price = events.get(0).getTradePrice();
                        // 결과가 있으면 캐시도 갱신해줌 (짧게)
                        String key = RedisKeyConstants.getTickerPriceKey(mode, universalCode);
                        redisTemplate.opsForValue().set(key, price, Duration.ofSeconds(60));
                        return price;
                    }
                    return null;
                });
    }
}
