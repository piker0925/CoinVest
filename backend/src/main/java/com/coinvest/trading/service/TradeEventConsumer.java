package com.coinvest.trading.service;

import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.trading.dto.TradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventConsumer {

    private final SseEmitters sseEmitters;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopicConstants.TRADE_ORDER_FILLED, groupId = "notification-service-group")
    public void consumeTradeEvent(ConsumerRecord<String, String> record) {
        try {
            TradeEvent event = objectMapper.readValue(record.value(), TradeEvent.class);
            log.debug("Received TradeEvent from Kafka: {}", event.tradeId());

            // Retry Hell 방지: SseEmitters.send 내부에서 IOException을 처리함.
            sseEmitters.send(event.userId(), "trade_filled", event);
            
        } catch (Exception e) {
            // 역직렬화 실패 등 예상치 못한 오류에 대한 안전망
            log.error("Failed to process TradeEvent from Kafka: {}", record.value(), e);
        }
    }
}
