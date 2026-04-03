package com.coinvest.trading.dto;

import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OrderPreviewRequest(
    @NotBlank String universalCode,
    @NotNull OrderSide side,
    @NotNull OrderType type,
    BigDecimal price,
    @NotNull @Positive BigDecimal quantity
) {}
