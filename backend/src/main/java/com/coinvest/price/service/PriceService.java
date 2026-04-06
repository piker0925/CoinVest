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
import java.util.List;
import java.util.Optional;

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
