package com.coinvest.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 포트폴리오 수정 요청 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpdateRequest {

    @NotBlank(message = "포트폴리오 이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
    private String name;

    @NotEmpty(message = "최소 한 개 이상의 자산을 포함해야 합니다.")
    @Size(max = 10, message = "포트폴리오당 최대 10개의 코인만 포함할 수 있습니다.")
    @Valid
    private List<PortfolioCreateRequest.AssetRequest> assets;
}
