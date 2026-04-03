package com.coinvest.portfolio.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 타입.
 */
@Getter
@RequiredArgsConstructor
public enum AlertType {
    REBALANCE_TRIGGERED("리밸런싱 발생"),
    SYSTEM_ERROR("시스템 오류"),
    ALERT_DISABLED("알림 비활성화됨");

    private final String description;
}
