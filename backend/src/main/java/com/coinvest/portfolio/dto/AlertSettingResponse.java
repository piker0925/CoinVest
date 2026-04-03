package com.coinvest.portfolio.dto;

import com.coinvest.portfolio.domain.AlertSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 알림 설정 응답 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertSettingResponse {
    private Long id;
    private String discordWebhookUrl;
    private BigDecimal deviationThreshold;
    private boolean isActive;

    public static AlertSettingResponse from(AlertSetting setting) {
        return AlertSettingResponse.builder()
                .id(setting.getId())
                .discordWebhookUrl(maskUrl(setting.getDiscordWebhookUrl()))
                .deviationThreshold(setting.getDeviationThreshold())
                .isActive(setting.isActive())
                .build();
    }

    private static String maskUrl(String url) {
        if (url == null || url.length() <= 20) {
            return url;
        }
        return url.substring(0, 20) + "********************";
    }
}
