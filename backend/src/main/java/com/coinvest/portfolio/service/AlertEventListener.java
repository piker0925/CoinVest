package com.coinvest.portfolio.service;

import com.coinvest.portfolio.event.RebalanceAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 리밸런싱 알림 이벤트를 처리하는 리스너.
 * 기존 AlertEventConsumer를 대체함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final AlertDispatcher alertDispatcher;

    /**
     * 알림 트리거 이벤트 수신 시 디스패처에 위임.
     */
    @Async
    @EventListener
    public void onAlertRebalanceTriggered(RebalanceAlertEvent event) {
        log.debug("Received rebalance alert event for portfolio: {}", event.valuation().getPortfolioId());
        alertDispatcher.dispatchRebalanceAlert(event.valuation());
    }
}
