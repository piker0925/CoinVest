package com.coinvest.price.service;

import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.TickerEvent;
import com.coinvest.trading.service.LimitOrderMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 실시간 가격 이벤트를 소비하여 Redis 캐시를 갱신하는 컨슈머.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    private final LimitOrderMatchingService limitOrderMatchingService;

    /**
     * Ticker 업데이트 이벤트 수신.
     */
    @KafkaListener(
            topics = KafkaTopicConstants.PRICE_TICKER_UPDATED,
            groupId = "price-cache-service",
            concurrency = "3"
    )
    public void onTickerUpdated(TickerEvent event) {
        String universalCode = event.getUniversalCode();
        String key = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, universalCode);
        
        // 1. 최신 가격 저장 (TTL 60초)
        redisTemplate.opsForValue().set(key, event.getTradePrice(), Duration.ofSeconds(60));
        
        // 2. 지정가 주문 매칭 시도
        try {
            limitOrderMatchingService.matchOrders(universalCode, event.getTradePrice());
        } catch (Exception e) {
            log.error("Failed to match limit orders for asset: {}", universalCode, e);
        }
        
        // 3. 해당 자산을 보유한 포트폴리오 ID 목록 조회
        String mappingKey = RedisKeyConstants.format(RedisKeyConstants.PORTFOLIO_ASSET_MAPPING_KEY, universalCode);
        java.util.Set<Object> portfolioIds = redisTemplate.opsForSet().members(mappingKey);

        if (portfolioIds != null && !portfolioIds.isEmpty()) {
            for (Object idObj : portfolioIds) {
                Long portfolioId = Long.valueOf(idObj.toString());
                // 4. 개별 포트폴리오 평가 이벤트를 별도 발행하여 분산 처리 유도
                kafkaTemplate.send(KafkaTopicConstants.EVALUATE_PORTFOLIO, String.valueOf(portfolioId), portfolioId);
            }
        }
        
        if (log.isTraceEnabled()) {
            log.trace("Updated Redis price cache: {} = {}", universalCode, event.getTradePrice());
        }
    }
}
