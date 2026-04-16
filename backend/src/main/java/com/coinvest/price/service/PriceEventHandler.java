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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
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

    private static final long CANDLE_INTERVAL_MS = 5 * 60 * 1000; // 5분
    private static final int MAX_WINDOW_SIZE = 120;

    /**
     * Time Bucketing의 원자성을 완벽히 보장하는 Lua 스크립트.
     * 슬롯 확인(GET) -> 조건에 따른 LSET 또는 LPUSH+LTRIM 로직을 단일 트랜잭션으로 묶어
     * 120개 초과 현상 및 캔들 중복 생성(Race Condition)을 원천 차단함.
     */
    private static final DefaultRedisScript<Long> BUCKET_SCRIPT;

    static {
        BUCKET_SCRIPT = new DefaultRedisScript<>();
        BUCKET_SCRIPT.setScriptText(
            "local slotKey = KEYS[1]; " +
            "local windowKey = KEYS[2]; " +
            "local currentSlot = ARGV[1]; " +
            "local priceStr = ARGV[2]; " +
            "local maxWindowSize = tonumber(ARGV[3]); " +
            "local lastSlot = redis.call('GET', slotKey); " +
            "if lastSlot == currentSlot then " +
            "    local len = redis.call('LLEN', windowKey); " +
            "    if len > 0 then " +
            "        redis.call('LSET', windowKey, 0, priceStr); " +
            "    else " +
            "        redis.call('LPUSH', windowKey, priceStr); " +
            "    end " +
            "else " +
            "    redis.call('SETEX', slotKey, 86400, currentSlot); " +
            "    redis.call('LPUSH', windowKey, priceStr); " +
            "    redis.call('LTRIM', windowKey, 0, maxWindowSize - 1); " +
            "end " +
            "return 1;"
        );
        BUCKET_SCRIPT.setResultType(Long.class);
    }

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

            // 1-1. 봇 전략 지표 계산용 가격 윈도우 갱신 (Time Bucketing)
            appendToPriceWindow(universalCode, event.getTradePrice(), event.getTradeTimestamp(), mode);

            // 2. 내부 리스너들을 위한 가격 업데이트 이벤트 발행 (mode 포함하여 Demo/Live 격리 보장)
            eventPublisher.publishEvent(new TickerUpdatedEvent(event, mode));

            // 3. 지정가 주문 매칭 시도
            try {
                limitOrderMatchingService.matchOrders(universalCode, event.getTradePrice(), mode, event.getTradeTimestamp());
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

    /**
     * Time Bucketing 로직: 틱(Tick) 수신 시 실시간으로 5분봉 종가를 캐싱.
     * Lua 스크립트를 사용하여 '슬롯 확인(Check) -> 갱신(Act)' 과정의 Race Condition을 원천 차단함.
     */
    private void appendToPriceWindow(String universalCode, Object price, Long timestamp, PriceMode mode) {
        try {
            long currentSlot = (timestamp != null ? timestamp : System.currentTimeMillis()) / CANDLE_INTERVAL_MS;
            
            String slotKey = RedisKeyConstants.getPriceWindowSlotKey(mode, universalCode);
            String windowKey = RedisKeyConstants.getPriceWindowKey(mode, universalCode);
            
            String priceStr = price.toString();
            String currentSlotStr = String.valueOf(currentSlot);
            String maxWindowSizeStr = String.valueOf(MAX_WINDOW_SIZE);

            redisTemplate.execute(
                    BUCKET_SCRIPT, 
                    List.of(slotKey, windowKey), 
                    currentSlotStr, priceStr, maxWindowSizeStr
            );
        } catch (Exception e) {
            log.warn("Failed to bucket price window for {}: {}", universalCode, e.getMessage());
        }
    }
}
