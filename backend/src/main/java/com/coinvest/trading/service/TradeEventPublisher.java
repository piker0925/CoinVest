package com.coinvest.trading.service;

import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.trading.dto.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeEvent(TradeEvent event) {
        try {
            kafkaTemplate.send(KafkaTopicConstants.TRADE_ORDER_FILLED, event.userId().toString(), event);
            log.info("Published TradeEvent for trade {} to Kafka", event.tradeId());
        } catch (Exception e) {
            log.error("Failed to publish TradeEvent to Kafka for trade {}", event.tradeId(), e);
        }
    }
}
