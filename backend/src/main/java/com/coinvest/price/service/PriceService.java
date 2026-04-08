package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.TickerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
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
