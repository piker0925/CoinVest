package com.coinvest.portfolio.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포트폴리오 실시간 평가 결과 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioValuation {

    private Long portfolioId;
    private BigDecimal totalEvaluationKrw;
    private List<AssetValuation> assetValuations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssetValuation {
        private String marketCode;
        private BigDecimal currentPrice;
        private BigDecimal quantity;
        private BigDecimal currentEvaluationKrw;
        private BigDecimal currentWeight; // 현재 가치 기반 비중 (0.0 ~ 1.0)
        private BigDecimal targetWeight;  // 목표 비중 (비교용)
    }
}
