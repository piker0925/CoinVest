package com.coinvest.trading.dto;

import com.coinvest.trading.domain.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeResponse(
    Long id,
    Long orderId,
    String marketCode,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal fee,
    BigDecimal realizedPnl,
    LocalDateTime createdAt
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
            trade.getId(),
            trade.getOrder().getId(),
            trade.getMarketCode(),
            trade.getPrice(),
            trade.getQuantity(),
            trade.getFee(),
            trade.getRealizedPnl(),
            trade.getCreatedAt()
        );
    }
}
