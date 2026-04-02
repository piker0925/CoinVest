package com.coinvest.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeEvent(
    Long tradeId,
    Long orderId,
    Long userId,
    String marketCode,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal fee,
    BigDecimal realizedPnl,
    LocalDateTime createdAt
) {}
