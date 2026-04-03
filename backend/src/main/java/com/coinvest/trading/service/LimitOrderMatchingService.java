package com.coinvest.trading.service;

import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.OrderStatus;
import com.coinvest.trading.domain.Position;
import com.coinvest.trading.domain.Trade;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.TradeRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
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
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");

    /**
     * Ticker 수신 시 호출되어 조건에 맞는 지정가 주문을 찾아 체결을 시도함.
     * 이 메서드 자체는 @Transactional을 붙이지 않아 I/O 작업을 트랜잭션에서 분리함.
     */
    public void matchOrders(String marketCode, BigDecimal currentPrice) {
        matchBuyOrders(marketCode, currentPrice);
        matchSellOrders(marketCode, currentPrice);
    }

    private void matchBuyOrders(String marketCode, BigDecimal currentPrice) {
        String key = "trading:limit-order:buy:" + marketCode;
        // 매수: 현재가보다 높거나 같은 지정가 주문을 찾음 (역순 조회: 높은 가격부터)
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
        // 조건부 업데이트: PENDING 상태일 때만 FILLED로 변경 (낙관적/비관적 락 대체)
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // 이미 처리됨. Redis에서 지우기 위해 true 반환(Self-healing).
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        
        BigDecimal quantity = order.getQuantity();
        BigDecimal orderPrice = order.getPrice(); // 원래 잠겼던 주문 가격 (보통 현재가보다 비싸거나 같음)
        
        // 1. 잠금 해제 (원래 주문가 기준으로 잠겼던 금액)
        BigDecimal lockedTotalAmount = orderPrice.multiply(quantity);
        BigDecimal lockedFee = BigDecimalUtil.formatKrw(lockedTotalAmount.multiply(FEE_RATE));
        BigDecimal lockedKrw = BigDecimalUtil.formatKrw(lockedTotalAmount.add(lockedFee));
        account.unlockBalance(lockedKrw);

        // 2. 실제 체결 (현재가 기준) 및 잔고 차감
        BigDecimal actualTotalAmount = currentPrice.multiply(quantity);
        BigDecimal actualFee = BigDecimalUtil.formatKrw(actualTotalAmount.multiply(FEE_RATE));
        BigDecimal actualKrw = BigDecimalUtil.formatKrw(actualTotalAmount.add(actualFee));
        account.decreaseBalance(actualKrw);

        // 3. 포지션 갱신
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

        // 4. Trade 기록
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
        Position position = positionRepository.findByUserIdAndUniversalCode(order.getUser().getId(), order.getUniversalCode()).orElseThrow();

        BigDecimal quantity = order.getQuantity();

        // 1. 수량 잠금 해제 (매도는 수량 자체가 잠김)
        position.unlockQuantity(quantity);

        // 2. 체결 및 잔고 증가 (현재가 기준)
        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedReturn = BigDecimalUtil.formatKrw(totalAmount.subtract(fee));

        BigDecimal realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);
        
        position.subtractPosition(currentPrice, quantity);
        account.increaseBalance(expectedReturn);

        // 3. Trade 기록
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
