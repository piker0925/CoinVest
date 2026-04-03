package com.coinvest.portfolio.dto;

import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 포트폴리오 응답 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioResponse {

    private Long id;
    private String name;
    private BigDecimal initialInvestmentKrw;
    private List<AssetResponse> assets;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssetResponse {
        private String universalCode;
        private BigDecimal targetWeight;
        private BigDecimal quantity;

        public static AssetResponse from(PortfolioAsset asset) {
            return AssetResponse.builder()
                    .universalCode(asset.getUniversalCode())
                    .targetWeight(asset.getTargetWeight())
                    .quantity(asset.getQuantity())
                    .build();
        }
    }

    public static PortfolioResponse from(Portfolio portfolio) {
        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .initialInvestmentKrw(portfolio.getInitialInvestmentKrw())
                .assets(portfolio.getAssets().stream()
                        .map(AssetResponse::from)
                        .collect(Collectors.toList()))
                .build();
    }
}
