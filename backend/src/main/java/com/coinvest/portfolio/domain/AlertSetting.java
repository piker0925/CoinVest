package com.coinvest.portfolio.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 포트폴리오별 알림 설정 엔티티.
 */
@Entity
@Table(name = "alert_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AlertSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Convert(converter = WebhookUrlConverter.class)
    @Column(name = "discord_webhook_url")
    private String discordWebhookUrl;

    /**
     * 편차 임계치 (예: 0.0500 = 5%).
     */
    @Builder.Default
    @Column(name = "deviation_threshold", nullable = false, precision = 20, scale = 4)
    private BigDecimal deviationThreshold = new BigDecimal("0.0500");

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * 알림 설정 업데이트.
     */
    public void update(String discordWebhookUrl, BigDecimal deviationThreshold) {
        this.discordWebhookUrl = discordWebhookUrl;
        this.deviationThreshold = deviationThreshold;
    }

    /**
     * 웹훅 장애 시 비활성화.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 사용자가 수동으로 활성화.
     */
    public void activate() {
        this.isActive = true;
    }
}
