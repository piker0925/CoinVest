package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LimitOrderMatchingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AssetRepository assetRepository;
    private final LimitOrderExecutor limitOrderExecutor;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * Ticker 수신 시 호출되어 조건에 맞는 지정가 주문을 찾아 체결을 시도함.
     *
     * @param tradeTimestamp 체결 이벤트 타임스탬프 (ms). null이면 시스템 현재 시각 사용.
     *                       백테스트 재사용 시 과거 타임스탬프를 주입하면 정산일이 정확히 계산됨.
     */
    public void matchOrders(String universalCode, BigDecimal currentPrice, PriceMode mode, Long tradeTimestamp) {
        LocalDate tradeDate = deriveTradeDate(tradeTimestamp);
        matchBuyOrders(universalCode, currentPrice, mode, tradeDate);
        matchSellOrders(universalCode, currentPrice, mode, tradeDate);
    }

    private void matchBuyOrders(String universalCode, BigDecimal currentPrice, PriceMode mode, LocalDate tradeDate) {
        String key = RedisKeyConstants.getLimitOrderKey(mode, "buy", universalCode);
        Set<Object> matchingOrderIds = redisTemplate.opsForZSet().reverseRangeByScore(key, currentPrice.doubleValue(), Double.MAX_VALUE);

        if (matchingOrderIds == null || matchingOrderIds.isEmpty()) return;

        // N+1 방지: 루프 밖에서 Asset 1회 조회 후 각 트랜잭션에 주입
        Asset asset = assetRepository.findByUniversalCode(universalCode)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + universalCode));

        for (Object orderIdObj : matchingOrderIds) {
            Long orderId = Long.valueOf(orderIdObj.toString());
            try {
                boolean filled = limitOrderExecutor.executeBuy(orderId, currentPrice, tradeDate, asset);
                if (filled) {
                    redisTemplate.opsForZSet().remove(key, orderIdObj);
                }
            } catch (Exception e) {
                log.error("Failed to execute buy limit order: {} (mode: {})", orderId, mode, e);
            }
        }
    }

    private void matchSellOrders(String universalCode, BigDecimal currentPrice, PriceMode mode, LocalDate tradeDate) {
        String key = RedisKeyConstants.getLimitOrderKey(mode, "sell", universalCode);
        Set<Object> matchingOrderIds = redisTemplate.opsForZSet().rangeByScore(key, 0, currentPrice.doubleValue());

        if (matchingOrderIds == null || matchingOrderIds.isEmpty()) return;

        // N+1 방지: 루프 밖에서 Asset 1회 조회 후 각 트랜잭션에 주입
        Asset asset = assetRepository.findByUniversalCode(universalCode)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + universalCode));

        for (Object orderIdObj : matchingOrderIds) {
            Long orderId = Long.valueOf(orderIdObj.toString());
            try {
                boolean filled = limitOrderExecutor.executeSell(orderId, currentPrice, tradeDate, asset);
                if (filled) {
                    redisTemplate.opsForZSet().remove(key, orderIdObj);
                }
            } catch (Exception e) {
                log.error("Failed to execute sell limit order: {} (mode: {})", orderId, mode, e);
            }
        }
    }

    /**
     * 타임스탬프(ms)로부터 KST 기준 체결일을 파생.
     * null이면 시스템 현재 시각을 사용.
     * KST 기준을 사용하는 이유: 자정 경계(23:59 KST)에서 UTC 기준 날짜와 달라지는 것을 방지.
     */
    private LocalDate deriveTradeDate(Long tradeTimestampMs) {
        long epochMs = tradeTimestampMs != null ? tradeTimestampMs : System.currentTimeMillis();
        return Instant.ofEpochMilli(epochMs).atZone(KST).toLocalDate();
    }
}
