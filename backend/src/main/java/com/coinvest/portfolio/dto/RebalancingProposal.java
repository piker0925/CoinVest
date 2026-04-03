package com.coinvest.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 리밸런싱 시뮬레이션 제안 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RebalancingProposal {
    private String marketCode;
    private BigDecimal currentWeight;
    private BigDecimal targetWeight;
    private BigDecimal deviation;
    private Action action;
    private BigDecimal proposedQuantity;
    private BigDecimal currentPrice;

    public enum Action {
        BUY, SELL, HOLD
    }
}
