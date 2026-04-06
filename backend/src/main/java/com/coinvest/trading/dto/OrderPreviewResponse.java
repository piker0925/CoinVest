package com.coinvest.trading.dto;

import java.math.BigDecimal;

public record OrderPreviewResponse(
    BigDecimal expectedPrice,
    BigDecimal expectedQuantity,
    BigDecimal expectedFee,
    BigDecimal expectedTotalAmount,
    boolean isReservation,
    BigDecimal estimatedExchangeAmount,
    String exchangeCurrency
) {}
