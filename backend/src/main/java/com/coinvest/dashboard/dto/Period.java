package com.coinvest.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * 대시보드 조회 기간 Enum.
 * - ONE_MONTH : 최근 1개월
 * - THREE_MONTHS: 최근 3개월
 * - ALL        : 포트폴리오 생성일 기준 전체
 */
@Getter
@RequiredArgsConstructor
public enum Period {

    ONE_MONTH("1M") {
        @Override
        public LocalDate getStartDate(LocalDate portfolioCreatedDate) {
            return LocalDate.now().minusMonths(1);
        }
    },
    THREE_MONTHS("3M") {
        @Override
        public LocalDate getStartDate(LocalDate portfolioCreatedDate) {
            return LocalDate.now().minusMonths(3);
        }
    },
    ALL("ALL") {
        @Override
        public LocalDate getStartDate(LocalDate portfolioCreatedDate) {
            return portfolioCreatedDate;
        }
    };

    @JsonValue
    private final String code;

    /**
     * 기간 시작 날짜 계산.
     * ALL 기간은 포트폴리오 생성일이 기준이므로 portfolioCreatedDate 필요.
     */
    public abstract LocalDate getStartDate(LocalDate portfolioCreatedDate);

    public static Period fromCode(String code) {
        for (Period p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        throw new IllegalArgumentException("Invalid period code: " + code);
    }
}
