package com.coinvest.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 주문 타입 (시장가/지정가).
 */
@Getter
@AllArgsConstructor
public enum OrderType {
    MARKET("시장가"),
    LIMIT("지정가");

    private final String description;
}
