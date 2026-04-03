package com.coinvest.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 알림 설정 수정 요청 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertSettingUpdateRequest {

    @NotBlank(message = "Discord Webhook URL은 필수입니다.")
    private String discordWebhookUrl;

    @NotNull(message = "편차 임계치는 필수입니다.")
    @DecimalMin(value = "0.0001", message = "최소 편차는 0.01% 이상이어야 합니다.")
    private BigDecimal deviationThreshold;
}
