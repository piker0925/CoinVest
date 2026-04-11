package com.coinvest.portfolio.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 알림 설정 엔티티.
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
    @JoinColumn(name = "portfolio_id", nullable = false, unique = true)
    private Portfolio portfolio;

    @Column(name = "discord_webhook_url", columnDefinition = "TEXT")
    private String discordWebhookUrl;

    /**
     * 리밸런싱 알림 임계치 (비중 편차).
     */
    @Column(name = "deviation_threshold", nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal deviationThreshold = new BigDecimal("0.05");

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * 기존 시그니처 유지 (하위 호환성).
     */
    public void update(String discordWebhookUrl, BigDecimal deviationThreshold) {
        this.discordWebhookUrl = discordWebhookUrl;
        this.deviationThreshold = deviationThreshold;
    }

    /**
     * 상태 변경을 포함한 업데이트 (오버로드).
     */
    public void update(String discordWebhookUrl, BigDecimal deviationThreshold, boolean isActive) {
        update(discordWebhookUrl, deviationThreshold);
        this.isActive = isActive;
    }

    /**
     * 알림 활성화.
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 알림 비활성화.
     */
    public void deactivate() {
        this.isActive = false;
    }
}
