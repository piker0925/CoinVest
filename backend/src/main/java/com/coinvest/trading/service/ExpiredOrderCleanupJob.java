package com.coinvest.trading.service;

import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.OrderStatus;
import com.coinvest.trading.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredOrderCleanupJob {

    private final OrderRepository orderRepository;
    private final TradingService tradingService;

    /**
     * 1시간마다 실행하여 생성 후 24시간이 지난 PENDING 주문을 EXPIRED로 변경하고 잠금 해제.
     * OOM 방지를 위해 500건씩 청크 처리.
     */
    @Scheduled(fixedDelay = 3600000)
    public void cleanupExpiredOrders() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(24);
        int pageSize = 500;
        int processedCount = 0;

        while (true) {
            // 매 반복마다 최신(남아있는) PENDING 주문 중 만료된 것을 가져옴
            Slice<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, thresholdTime, PageRequest.of(0, pageSize));
            
            if (expiredOrders.isEmpty()) {
                break;
            }

            for (Order order : expiredOrders) {
                try {
                    processExpiredOrder(order);
                    processedCount++;
                } catch (Exception e) {
                    log.error("Failed to expire order: {}", order.getId(), e);
                }
            }
        }
        
        if (processedCount > 0) {
            log.info("ExpiredOrderCleanupJob completed. Expired {} orders.", processedCount);
        }
    }

    @Transactional
    public void processExpiredOrder(Order order) {
        tradingService.cancelOrder(order.getUser().getId(), order.getId());
        
        // cancelOrder는 내부적으로 CANCELLED로 만드므로 EXPIRED로 덮어쓰기
        order.expire();
        orderRepository.save(order);
    }
}
