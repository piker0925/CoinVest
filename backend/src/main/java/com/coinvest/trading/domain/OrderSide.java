package com.coinvest.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 주문 방향 (매수/매도).
 */
@Getter
@AllArgsConstructor
public enum OrderSide {
    BUY("매수"),
    SELL("매도");

    private final String description;
}
