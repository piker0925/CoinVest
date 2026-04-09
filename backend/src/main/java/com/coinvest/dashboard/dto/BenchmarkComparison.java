package com.coinvest.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/dashboard/benchmark 응답 DTO.
 * 나 vs 벤치마크 지수 vs 봇 전략 수익률 비교.
 */
@Getter
@Builder
public class BenchmarkComparison {

    /** 내 수익률 (%) — 동일 기간 기준 */
    private final BigDecimal myReturn;

    /**
     * 벤치마크 지수별 수익률.
     * Demo: KOSPI_SIM, SP500_SIM (가상 지수)
     * Live: KOSPI, SP500 (실제 지수)
     */
    private final List<IndexReturn> indexReturns;

    /**
     * 봇 전략별 수익률.
     * 6A 구현 전: 빈 배열 반환.
     * 프론트엔드에서 isEmpty() 체크 후 조건부 렌더링.
     */
    private final List<BotReturn> botReturns;

    /** 조회 기간 ("1M", "3M", "ALL") */
    private final String period;

    @Getter
    @Builder
    public static class IndexReturn {
        /** 지수 코드 (KOSPI_SIM, SP500_SIM, KOSPI, SP500 등) */
        private final String code;
        /** 지수 표시 이름 */
        private final String name;
        /** 기간 수익률 (%) */
        private final BigDecimal returnRate;
        /** 기간 시작 시점 지수 값 */
        private final BigDecimal startValue;
        /** 현재 지수 값 */
        private final BigDecimal endValue;
    }

    @Getter
    @Builder
    public static class BotReturn {
        /** 봇 전략 이름 (INDEX_TRACKING, MOMENTUM 등) */
        private final String strategyName;
        /** 기간 수익률 (%) */
        private final BigDecimal returnRate;
    }
}
