package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.repository.*;
import com.coinvest.trading.dto.TradeEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.coinvest.global.util.BigDecimalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final OrderRepository orderRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final BalanceRepository balanceRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AssetRepository assetRepository;
    private final MarketHoursService marketHoursService;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");
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

        for (Object orderIdObj : matchingOrderIds) {
            Long orderId = Long.valueOf(orderIdObj.toString());
            try {
                boolean filled = executeBuyOrderInTransaction(orderId, currentPrice, tradeDate);
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

        for (Object orderIdObj : matchingOrderIds) {
            Long orderId = Long.valueOf(orderIdObj.toString());
            try {
                boolean filled = executeSellOrderInTransaction(orderId, currentPrice, tradeDate);
                if (filled) {
                    redisTemplate.opsForZSet().remove(key, orderIdObj);
                }
            } catch (Exception e) {
                log.error("Failed to execute sell limit order: {} (mode: {})", orderId, mode, e);
            }
        }
    }

    /**
     * 개별 매수 주문 체결 트랜잭션 (Short Transaction)
     *
     * @param tradeDate 체결 기준일 (KST). 정산일 계산의 기준이 됨.
     */
    @Transactional
    public boolean executeBuyOrderInTransaction(Long orderId, BigDecimal currentPrice, LocalDate tradeDate) {
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // Self-healing
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        PriceMode mode = order.getPriceMode();
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        
        // 지정가 매수 시 사용된 통화의 잔고를 잠금 해제 및 차감
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(account.getId(), order.getCurrency())
                .orElseThrow(() -> new RuntimeException("Balance not found"));
        
        BigDecimal quantity = order.getQuantity();
        BigDecimal orderPrice = order.getPrice();
        
        BigDecimal lockedTotalAmount = orderPrice.multiply(quantity);
        BigDecimal lockedFee = BigDecimalUtil.formatKrw(lockedTotalAmount.multiply(FEE_RATE));
        BigDecimal lockedKrw = BigDecimalUtil.formatKrw(lockedTotalAmount.add(lockedFee));
        
        balance.unlock(lockedKrw);

        BigDecimal actualTotalAmount = currentPrice.multiply(quantity);
        BigDecimal actualFee = BigDecimalUtil.formatKrw(actualTotalAmount.multiply(FEE_RATE));
        BigDecimal actualKrw = BigDecimalUtil.formatKrw(actualTotalAmount.add(actualFee));
        
        balance.decreaseAvailable(actualKrw);

        Position position = positionRepository.findByUserIdAndUniversalCodeAndPriceMode(order.getUser().getId(), order.getUniversalCode(), mode)
                .orElseGet(() -> Position.builder()
                        .user(order.getUser())
                        .universalCode(order.getUniversalCode())
                        .priceMode(mode)
                        .currency(order.getCurrency())
                        .avgBuyPrice(BigDecimal.ZERO)
                        .quantity(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .build());
        position.addPosition(currentPrice, quantity);
        positionRepository.save(position);

        Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode())
                .orElseThrow(() -> new RuntimeException("Asset not found: " + order.getUniversalCode()));
        LocalDate settlementDate = marketHoursService.calculateSettlementDate(asset, tradeDate);

        Trade trade = Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .currency(order.getCurrency())
                .price(currentPrice)
                .quantity(quantity)
                .fee(actualFee)
                .realizedPnl(BigDecimal.ZERO)
                .priceMode(mode)
                .settlementDate(settlementDate)
                .build();
        trade = tradeRepository.save(trade);

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(),
                order.getId(),
                order.getUser().getId(),
                order.getUniversalCode(),
                currentPrice,
                quantity,
                actualFee,
                trade.getRealizedPnl(),
                trade.getCreatedAt()
        ));

        return true;
    }

    /**
     * 개별 매도 주문 체결 트랜잭션 (Short Transaction)
     *
     * @param tradeDate 체결 기준일 (KST). 정산일 계산의 기준이 됨.
     */
    @Transactional
    public boolean executeSellOrderInTransaction(Long orderId, BigDecimal currentPrice, LocalDate tradeDate) {
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // Self-healing
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        PriceMode mode = order.getPriceMode();
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(account.getId(), order.getCurrency())
                .orElseThrow(() -> new RuntimeException("Balance not found"));
        
        Position position = positionRepository.findByUserIdAndUniversalCodeAndPriceMode(order.getUser().getId(), order.getUniversalCode(), mode).orElseThrow();

        BigDecimal quantity = order.getQuantity();

        position.unlockQuantity(quantity);

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedReturn = BigDecimalUtil.formatKrw(totalAmount.subtract(fee));

        BigDecimal realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);
        
        position.subtractPosition(currentPrice, quantity);
        balance.increaseAvailable(expectedReturn);

        Asset sellAsset = assetRepository.findByUniversalCode(order.getUniversalCode())
                .orElseThrow(() -> new RuntimeException("Asset not found: " + order.getUniversalCode()));
        LocalDate settlementDate = marketHoursService.calculateSettlementDate(sellAsset, tradeDate);

        Trade trade = Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .currency(order.getCurrency())
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimalUtil.formatKrw(realizedPnl))
                .priceMode(mode)
                .settlementDate(settlementDate)
                .build();
        trade = tradeRepository.save(trade);

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(),
                order.getId(),
                order.getUser().getId(),
                order.getUniversalCode(),
                currentPrice,
                quantity,
                fee,
                trade.getRealizedPnl(),
                trade.getCreatedAt()
        ));

        return true;
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
