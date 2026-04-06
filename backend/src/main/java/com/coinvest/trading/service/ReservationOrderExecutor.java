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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationOrderExecutor {

    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final MarketHoursService marketHoursService;
    private final TradingService tradingService;

    /**
     * 1분마다 예약 주문 확인 및 병렬 실행.
     * 장 개시 시 Thundering Herd 문제를 방어하기 위해 Virtual Threads 사용.
     */
    @Scheduled(fixedRate = 60000)
    public void executeReservedOrders() {
        // PENDING 상태인 예약 주문 조회
        List<Order> reservedOrders = orderRepository.findAllByReservationAndStatus(true, OrderStatus.PENDING);

        if (reservedOrders.isEmpty()) {
            return;
        }

        log.info("Starting execution for {} reserved orders using Virtual Threads...", reservedOrders.size());

        // Java 21 Virtual Threads 활용 (경량 스레드로 수만 건도 동시 처리 가능)
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Order order : reservedOrders) {
                executor.submit(() -> processSingleOrder(order));
            }
        } catch (Exception e) {
            log.error("Critical error during reserved orders parallel execution", e);
        }
    }

    private void processSingleOrder(Order order) {
        try {
            Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElse(null);
            if (asset != null && marketHoursService.isMarketOpen(asset)) {
                log.debug("Market opened for reserved order ID: {}. Processing...", order.getId());
                tradingService.processReservedOrder(order.getId());
            }
        } catch (Exception e) {
            log.error("Failed to process reserved order ID: {}", order.getId(), e);
        }
    }
}
