package com.coinvest.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 주문 처리 상태.
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {
    PENDING("대기"),
    FILLED("체결"),
    CANCELLED("취소"),
    EXPIRED("만료");

    private final String description;
}
