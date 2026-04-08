package com.coinvest.trading.service;

import com.coinvest.global.common.PriceMode;
import com.coinvest.price.event.TickerUpdatedEvent;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.OrderType;
import com.coinvest.trading.domain.StopLossOrder;
import com.coinvest.trading.domain.TakeProfitOrder;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.repository.StopLossOrderRepository;
import com.coinvest.trading.repository.TakeProfitOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopLossTakeProfitMatcher {

    private final StopLossOrderRepository stopLossOrderRepository;
    private final TakeProfitOrderRepository takeProfitOrderRepository;
    private final TradingService tradingService;

    @Async
    @EventListener
    public void onTickerUpdated(TickerUpdatedEvent event) {
        String universalCode = event.ticker().getUniversalCode();
        BigDecimal currentPrice = event.ticker().getTradePrice();

        matchStopLoss(universalCode, currentPrice);
        matchTakeProfit(universalCode, currentPrice);
    }

    @Transactional
    public void matchStopLoss(String universalCode, BigDecimal currentPrice) {
        List<StopLossOrder> triggeredOrders = stopLossOrderRepository.findAllActiveTriggered(universalCode, currentPrice);
        
        for (StopLossOrder order : triggeredOrders) {
            try {
                order.process();
                // 포지션의 priceMode를 참조하여 주문 (Position 엔티티에 priceMode 필드가 있다고 가정)
                PriceMode mode = order.getPosition().getPriceMode();
                executeAutoSell(order.getUser().getId(), universalCode, order.getQuantity(), mode);
                order.execute();
                log.info("Stop-Loss Executed: [User={}, Asset={}, Price={}, Mode={}]", 
                        order.getUser().getId(), universalCode, currentPrice, mode);
            } catch (Exception e) {
                log.error("Failed to execute Stop-Loss order: {}", order.getId(), e);
                order.fail();
            }
        }
    }

    @Transactional
    public void matchTakeProfit(String universalCode, BigDecimal currentPrice) {
        List<TakeProfitOrder> triggeredOrders = takeProfitOrderRepository.findAllActiveTriggered(universalCode, currentPrice);

        for (TakeProfitOrder order : triggeredOrders) {
            try {
                order.process();
                PriceMode mode = order.getPosition().getPriceMode();
                executeAutoSell(order.getUser().getId(), universalCode, order.getQuantity(), mode);
                order.execute();
                log.info("Take-Profit Executed: [User={}, Asset={}, Price={}, Mode={}]", 
                        order.getUser().getId(), universalCode, currentPrice, mode);
            } catch (Exception e) {
                log.error("Failed to execute Take-Profit order: {}", order.getId(), e);
                order.fail();
            }
        }
    }

    private void executeAutoSell(Long userId, String universalCode, BigDecimal quantity, PriceMode mode) {
        OrderCreateRequest request = new OrderCreateRequest(
                universalCode,
                OrderSide.SELL,
                OrderType.MARKET,
                null,
                quantity
        );
        tradingService.createOrder(userId, request, mode);
    }
}
