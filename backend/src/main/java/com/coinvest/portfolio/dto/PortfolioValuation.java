package com.coinvest.portfolio.dto;

import com.coinvest.fx.domain.Currency;
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
    private BigDecimal totalEvaluationBase; // 기준 통화(Base) 합계 가치 (자산만)
    private BigDecimal buyingPowerBase;     // 계좌 가용 현금 (기준 통화 환산)
    private Currency baseCurrency;          // 기준 통화 (KRW/USD)
    private boolean isStaleExchangeRate;    // 환율 정보가 지연(48h+)되었는지 여부
    private List<AssetValuation> assetValuations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssetValuation {
        private String universalCode;
        private BigDecimal currentPrice;        // 해당 자산 통화 기준 가격
        private BigDecimal quantity;
        private BigDecimal evaluationNative;    // 자산 통화 기준 가치 (Price * Qty)
        private BigDecimal evaluationBase;      // 기준 통화 환산 가치 (Native * FxRate)
        private BigDecimal fxRate;              // 적용 환율 (Base / Native)
        private Currency quoteCurrency;         // 자산의 표시 통화
        private BigDecimal currentWeight;       // 전체 포트폴리오 내 비중 (0.0 ~ 1.0)
        private BigDecimal targetWeight;        // 목표 비중 (비교용)
    }
}
