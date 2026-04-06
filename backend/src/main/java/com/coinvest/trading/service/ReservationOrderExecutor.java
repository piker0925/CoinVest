package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.OrderStatus;
import com.coinvest.trading.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationOrderExecutor {

    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final MarketHoursService marketHoursService;
    private final TradingService tradingService;

    /**
     * 1분마다 예약 주문 확인 및 실행
     */
    @Scheduled(fixedRate = 60000)
    public void executeReservedOrders() {
        List<Order> reservedOrders = orderRepository.findAllByReservationAndStatus(true, OrderStatus.PENDING);

        if (reservedOrders.isEmpty()) {
            return;
        }

        log.info("Checking {} reserved orders...", reservedOrders.size());

        for (Order order : reservedOrders) {
            Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElse(null);
            if (asset != null && marketHoursService.isMarketOpen(asset)) {
                try {
                    // 예약 주문 실행 (TradingService 내부에 별도 실행 로직 필요할 수 있음)
                    // 현재는 단순화를 위해 로그 출력 후 처리 로직 위임 구조만 생성
                    log.info("Market opened for reserved order ID: {}. Triggering execution...", order.getId());
                    processReservedOrder(order);
                } catch (Exception e) {
                    log.error("Failed to execute reserved order ID: {}", order.getId(), e);
                }
            }
        }
    }

    private void processReservedOrder(Order order) {
        // 예약 주문 실행 시 TradingService의 전용 로직 호출
        tradingService.processReservedOrder(order.getId());
        log.info("Successfully triggered execution for reserved order: {}", order.getId());
    }
}
