package com.coinvest.portfolio.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 포트폴리오 엔티티.
 */
@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Portfolio extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "initial_investment", nullable = false, precision = 38, scale = 20)
    private BigDecimal initialInvestment;

    @Column(name = "net_contribution", nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal netContribution = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", nullable = false, length = 10)
    @Builder.Default
    private Currency baseCurrency = Currency.KRW;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    @Builder.Default
    private PriceMode priceMode = PriceMode.LIVE;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PortfolioAsset> assets = new ArrayList<>();

    public void addAsset(PortfolioAsset asset) {
        this.assets.add(asset);
        asset.assignPortfolio(this);
    }

    public void updateContribution(BigDecimal amount) {
        this.netContribution = this.netContribution.add(amount);
    }

    public void update(String name, BigDecimal initialInvestment, Currency baseCurrency) {
        this.name = name;
        this.initialInvestment = initialInvestment;
        this.baseCurrency = baseCurrency;
    }
}
