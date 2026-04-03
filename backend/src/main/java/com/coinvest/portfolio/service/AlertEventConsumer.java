package com.coinvest.portfolio.service;

import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.portfolio.dto.PortfolioValuation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 리밸런싱 알림 트리거 이벤트를 소비하는 컨슈머.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventConsumer {

    private final AlertDispatcher alertDispatcher;

    /**
     * 알림 트리거 이벤트 수신 시 디스패처에 위임.
     * (디스패처 내부에서 비동기 + 재시도 처리됨)
     */
    @KafkaListener(
            topics = KafkaTopicConstants.ALERT_REBALANCE_TRIGGERED,
            groupId = "alert-service-group"
    )
    public void onAlertRebalanceTriggered(PortfolioValuation valuation) {
        log.debug("Received rebalance alert event for portfolio: {}", valuation.getPortfolioId());
        
        // 비동기 메서드 호출 -> 즉시 오프셋 커밋 가능 (컨슈머 랙 방어)
        alertDispatcher.dispatchRebalanceAlert(valuation);
    }
}
