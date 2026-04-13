package com.coinvest.trading.dto;

import com.coinvest.trading.domain.Position;

import java.math.BigDecimal;

public record PositionResponse(
    Long id,
    String universalCode,
    String currency,
    BigDecimal avgBuyPrice,
    BigDecimal quantity,
    BigDecimal lockedQuantity,
    BigDecimal availableQuantity,
    BigDecimal realizedPnl,
    BigDecimal currentPrice,
    BigDecimal evaluationAmount,
    BigDecimal unrealizedPnl,
    BigDecimal returnRate
) {
    public static PositionResponse of(Position position, BigDecimal currentPrice) {
        BigDecimal evalAmount = currentPrice.multiply(position.getQuantity());
        BigDecimal totalCost = position.getAvgBuyPrice().multiply(position.getQuantity());
        BigDecimal unrealizedPnl = evalAmount.subtract(totalCost);
        
        BigDecimal returnRate = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = unrealizedPnl.divide(totalCost, 4, java.math.RoundingMode.HALF_UP)
                                      .multiply(new BigDecimal("100"));
        }

        return new PositionResponse(
            position.getId(),
            position.getUniversalCode(),
            position.getCurrency().name(),
            position.getAvgBuyPrice(),
            position.getQuantity(),
            position.getLockedQuantity(),
            position.getAvailableQuantity(),
            position.getRealizedPnl(),
            currentPrice,
            evalAmount,
            unrealizedPnl,
            returnRate
        );
    }
}
