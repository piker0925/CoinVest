package com.coinvest.trading.dto;

import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.OrderStatus;
import com.coinvest.trading.domain.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
    Long id,
    String marketCode,
    OrderSide side,
    OrderType type,
    BigDecimal price,
    BigDecimal quantity,
    OrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime filledAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getMarketCode(),
            order.getSide(),
            order.getType(),
            order.getPrice(),
            order.getQuantity(),
            order.getStatus(),
            order.getCreatedAt(),
            order.getFilledAt()
        );
    }
}
