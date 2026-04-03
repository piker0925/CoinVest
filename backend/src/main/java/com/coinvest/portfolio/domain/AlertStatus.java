package com.coinvest.portfolio.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 처리 상태.
 */
@Getter
@RequiredArgsConstructor
public enum AlertStatus {
    SUCCESS("성공"),
    FAILED("실패");

    private final String description;
}
