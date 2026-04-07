package com.coinvest.trading.service;

import com.coinvest.fx.domain.Currency;
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

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");

    /**
     * Ticker 수신 시 호출되어 조건에 맞는 지정가 주문을 찾아 체결을 시도함.
     */
    public void matchOrders(String universalCode, BigDecimal currentPrice) {
        matchBuyOrders(universalCode, currentPrice);
        matchSellOrders(universalCode, currentPrice);
    }

    private void matchBuyOrders(String universalCode, BigDecimal currentPrice) {
        String key = "trading:limit-order:buy:" + universalCode;
        // 매수: 현재가보다 높거나 같은 지정가 주문을 찾음
        Set<Object> matchingOrderIds = redisTemplate.opsForZSet().reverseRangeByScore(key, currentPrice.doubleValue(), Double.MAX_VALUE);
        
        if (matchingOrderIds == null || matchingOrderIds.isEmpty()) return;

        for (Object orderIdObj : matchingOrderIds) {
            Long orderId = Long.valueOf(orderIdObj.toString());
            try {
                boolean filled = executeBuyOrderInTransaction(orderId, currentPrice);
                if (filled) {
                    redisTemplate.opsForZSet().remove(key, orderIdObj);
                }
            } catch (Exception e) {
                log.error("Failed to execute buy limit order: {}", orderId, e);
            }
        }
    }

    private void matchSellOrders(String universalCode, BigDecimal currentPrice) {
        String key = "trading:limit-order:sell:" + universalCode;
        // 매도: 현재가보다 낮거나 같은 지정가 주문을 찾음
        Set<Object> matchingOrderIds = redisTemplate.opsForZSet().rangeByScore(key, 0, currentPrice.doubleValue());
        
        if (matchingOrderIds == null || matchingOrderIds.isEmpty()) return;

        for (Object orderIdObj : matchingOrderIds) {
            Long orderId = Long.valueOf(orderIdObj.toString());
            try {
                boolean filled = executeSellOrderInTransaction(orderId, currentPrice);
                if (filled) {
                    redisTemplate.opsForZSet().remove(key, orderIdObj);
                }
            } catch (Exception e) {
                log.error("Failed to execute sell limit order: {}", orderId, e);
            }
        }
    }

    /**
     * 개별 매수 주문 체결 트랜잭션 (Short Transaction)
     */
    @Transactional
    public boolean executeBuyOrderInTransaction(Long orderId, BigDecimal currentPrice) {
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // Self-healing
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
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

        Position position = positionRepository.findByUserIdAndUniversalCode(order.getUser().getId(), order.getUniversalCode())
                .orElseGet(() -> Position.builder()
                        .user(order.getUser())
                        .universalCode(order.getUniversalCode())
                        .avgBuyPrice(BigDecimal.ZERO)
                        .quantity(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .build());
        position.addPosition(currentPrice, quantity);
        positionRepository.save(position);

        Trade trade = Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .price(currentPrice)
                .quantity(quantity)
                .fee(actualFee)
                .realizedPnl(BigDecimal.ZERO)
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
     */
    @Transactional
    public boolean executeSellOrderInTransaction(Long orderId, BigDecimal currentPrice) {
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // Self-healing
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(account.getId(), order.getCurrency())
                .orElseThrow(() -> new RuntimeException("Balance not found"));
        
        Position position = positionRepository.findByUserIdAndUniversalCode(order.getUser().getId(), order.getUniversalCode()).orElseThrow();

        BigDecimal quantity = order.getQuantity();

        position.unlockQuantity(quantity);

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedReturn = BigDecimalUtil.formatKrw(totalAmount.subtract(fee));

        BigDecimal realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);
        
        position.subtractPosition(currentPrice, quantity);
        balance.increaseAvailable(expectedReturn);

        Trade trade = Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimalUtil.formatKrw(realizedPnl))
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
}
