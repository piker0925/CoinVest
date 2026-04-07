package com.coinvest.portfolio.dto;
import com.coinvest.fx.domain.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포트폴리오 생성 요청 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioCreateRequest {

    @NotBlank(message = "포트폴리오 이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
    private String name;

    @NotNull(message = "초기 투자 금액을 입력해주세요.")
    @DecimalMin(value = "5000", message = "최소 투자 금액은 5,000 KRW 이상이어야 합니다.")
    private BigDecimal initialInvestment;

    @NotNull(message = "기준 통화를 선택해주세요.")
    private Currency baseCurrency;

    @NotEmpty(message = "최소 한 개 이상의 자산을 포함해야 합니다.")
    @Size(max = 10, message = "포트폴리오당 최대 10개의 코인만 포함할 수 있습니다.")
    @Valid
    private List<AssetRequest> assets;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetRequest {
        @NotBlank(message = "범용 자산 코드를 입력해주세요.")
        private String universalCode;

        @NotNull(message = "목표 비중을 입력해주세요.")
        @DecimalMin(value = "0.01", message = "비중은 최소 0.01(1%) 이상이어야 합니다.")
        @DecimalMax(value = "1.00", message = "비중은 최대 1.00(100%) 이내여야 합니다.")
        private BigDecimal targetWeight;
    }
}
