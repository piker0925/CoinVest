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

            SseEmitter emitter = sseEmitters.get(event.userId());
            if (emitter != null) {
                try {
                    // Retry Hell 방지: 전송 실패 시 예외를 스왈로윙(로깅 후 리스트에서 삭제)하여 Kafka 오프셋이 정상 진행되게 함.
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.tradeId()))
                            .name("trade_filled")
                            .data(event));
                } catch (IOException e) {
                    log.warn("Failed to send TradeEvent to user {}. Removing emitter.", event.userId());
                    sseEmitters.remove(event.userId());
                }
            }
        } catch (Exception e) {
            // 역직렬화 실패 등 예상치 못한 오류에 대한 안전망
            log.error("Failed to process TradeEvent from Kafka: {}", record.value(), e);
        }
    }
}
