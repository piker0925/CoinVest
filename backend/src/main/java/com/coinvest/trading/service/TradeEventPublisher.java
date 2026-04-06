package com.coinvest.trading.service;

import com.coinvest.trading.dto.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventPublisher {

    private final SseEmitters sseEmitters;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeEvent(TradeEvent event) {
        try {
            sseEmitters.send(event.userId(), "trade-filled", event);
            log.info("Published TradeEvent for trade {} via SSE", event.tradeId());
        } catch (Exception e) {
            log.error("Failed to publish TradeEvent via SSE for trade {}", event.tradeId(), e);
        }
    }
}
