package com.coinvest.price.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderbookResponse {
    private String universalCode;
    private List<OrderbookUnit> sells;
    private List<OrderbookUnit> buys;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderbookUnit {
        private BigDecimal price;
        private BigDecimal quantity;
        private double ratio; // UI 표현을 위한 비중 (0~100)
    }
}
