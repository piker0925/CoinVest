package com.coinvest.portfolio.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 포트폴리오 개별 자산 엔티티.
 */
@Entity
@Table(name = "portfolio_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PortfolioAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 업비트 마켓 코드 (예: KRW-BTC).
     */
    @Column(name = "market_code", nullable = false)
    private String marketCode;

    /**
     * 목표 비중 (0.0 ~ 1.0).
     */
    @Column(name = "target_weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal targetWeight;

    /**
     * 현재 보유 수량.
     */
    @Column(nullable = false, precision = 30, scale = 18)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    /**
     * 연관 관계 편의 메서드.
     */
    protected void assignPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    /**
     * 자산 정보 업데이트.
     */
    public void update(BigDecimal targetWeight, BigDecimal quantity) {
        this.targetWeight = targetWeight;
        this.quantity = quantity;
    }
}
