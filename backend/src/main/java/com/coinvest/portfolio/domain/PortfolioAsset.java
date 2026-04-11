package com.coinvest.portfolio.domain;

import com.coinvest.fx.domain.Currency;
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

    @Column(name = "universal_code", nullable = false, length = 50)
    private String universalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Currency currency = Currency.KRW;

    @Column(name = "target_weight", nullable = false, precision = 10, scale = 6)
    private BigDecimal targetWeight;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    protected void assignPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public void update(BigDecimal targetWeight, BigDecimal quantity, Currency currency) {
        this.targetWeight = targetWeight;
        this.quantity = quantity;
        this.currency = currency;
    }
}
