package com.coinvest.dashboard.dto;

import com.coinvest.fx.domain.Currency;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * GET /api/v1/dashboard/performance 응답 DTO.
 * 개인 투자 성과 (수익률, 총자산, 순기여금, 수익금).
 */
@Getter
@Builder
public class PerformanceResponse {

    private final Long portfolioId;
    private final String portfolioName;

    /** 현재 총자산 (자산 평가액 + 현금, 기준 통화) */
    private final BigDecimal totalValue;

    /** 순기여금 (초기투자 + 추가입금 - 출금) */
    private final BigDecimal netContribution;

    /** 수익금 = totalValue - netContribution */
    private final BigDecimal profitLoss;

    /**
     * 수익률 (%).
     * Modified Simple Return 공식 적용.
     * - ALL  : (V_now - NC_now) / NC_now × 100
     * - 1M/3M: (V_now - V_start - (NC_now - NC_start)) / V_start × 100
     */
    private final BigDecimal returnRate;

    /** 기준 통화 */
    private final Currency baseCurrency;

    /** 조회 기간 ("1M", "3M", "ALL") */
    private final String period;

    /** 환율 정보가 48h 이상 지연된 경우 true */
    private final boolean staleExchangeRate;
}
