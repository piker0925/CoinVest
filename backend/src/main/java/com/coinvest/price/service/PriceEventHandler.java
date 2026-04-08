package com.coinvest.price.service;

import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.event.PortfolioValuationEvent;
import com.coinvest.price.dto.TickerEvent;
import com.coinvest.price.event.TickerUpdatedEvent;
import com.coinvest.trading.service.LimitOrderMatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * Redis Pub/Sub을 통해 실시간 가격 이벤트를 수신하고 캐시 갱신 및 후속 작업을 처리함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceEventHandler implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LimitOrderMatchingService limitOrderMatchingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            PriceMode mode = channel.contains(PriceMode.DEMO.getPrefix()) ? PriceMode.DEMO : PriceMode.LIVE;

            TickerEvent event = objectMapper.readValue(message.getBody(), TickerEvent.class);
            String universalCode = event.getUniversalCode();
            String key = RedisKeyConstants.getTickerPriceKey(mode, universalCode);

            // 1. 최신 가격 저장 (TTL 60초)
            redisTemplate.opsForValue().set(key, event.getTradePrice(), Duration.ofSeconds(60));

            // 2. 내부 리스너들을 위한 가격 업데이트 이벤트 발행
            eventPublisher.publishEvent(new TickerUpdatedEvent(event));

            // 3. 지정가 주문 매칭 시도
            try {
                limitOrderMatchingService.matchOrders(universalCode, event.getTradePrice(), mode);
            } catch (Exception e) {
                log.error("Failed to match limit orders for asset: {} (mode: {})", universalCode, mode, e);
            }

            // 4. 해당 자산을 보유한 포트폴리오 ID 목록 조회 후 평가 이벤트 발행
            String mappingKey = RedisKeyConstants.getPortfolioAssetMappingKey(mode, universalCode);
            Set<Object> portfolioIds = redisTemplate.opsForSet().members(mappingKey);

            if (portfolioIds != null && !portfolioIds.isEmpty()) {
                for (Object idObj : portfolioIds) {
                    Long portfolioId = Long.valueOf(idObj.toString());
                    eventPublisher.publishEvent(new PortfolioValuationEvent(portfolioId));
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Updated Redis price cache via Pub/Sub: {} = {} (mode: {})", universalCode, event.getTradePrice(), mode);
            }

        } catch (Exception e) {
            log.error("Failed to handle Redis price message", e);
        }
    }
}
