package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.TickerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    /**
     * 현재가 조회.
     * 1. Redis 캐시 확인.
     * 2. 캐시 미스 시 적절한 Provider를 통해 실시간 조회 시도.
     */
    public BigDecimal getCurrentPrice(String universalCode) {
        String key = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, universalCode);
        Object priceObj = redisTemplate.opsForValue().get(key);

        if (priceObj != null) {
            return new BigDecimal(priceObj.toString());
        }

        // 캐시 미스 시 실시간 조회 시도
        return fetchRealtimePrice(universalCode).orElse(BigDecimal.ZERO);
    }

    /**
     * 다중 자산 가격 배치 조회 (성능 최적화).
     * Redis MGET 사용.
     */
    public Map<String, BigDecimal> getPrices(List<String> universalCodes) {
        if (universalCodes == null || universalCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = universalCodes.stream()
                .map(code -> RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, code))
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
                        // 캐시 미스 시 개별 조회 (또는 전체 배치 조회 로직 고도화 가능)
                        return getCurrentPrice(universalCodes.get(i));
                    }
                ));
    }

    private Optional<BigDecimal> fetchRealtimePrice(String universalCode) {
        log.info("Price cache miss for {}. Fetching from provider...", universalCode);
        
        return assetRepository.findByUniversalCode(universalCode)
                .map(asset -> {
                    List<TickerEvent> events = priceProviderRouter.fetchPrices(List.of(asset));
                    if (!events.isEmpty()) {
                        BigDecimal price = events.get(0).getTradePrice();
                        // 결과가 있으면 캐시도 갱신해줌 (짧게)
                        String key = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, universalCode);
                        redisTemplate.opsForValue().set(key, price, java.time.Duration.ofSeconds(60));
                        return price;
                    }
                    return null;
                });
    }
}
