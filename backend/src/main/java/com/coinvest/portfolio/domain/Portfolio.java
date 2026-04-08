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
 * 사용자가 설정한 가상의 자산 군을 관리함.
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

    /**
     * 초기 투자 금액 (기준 통화 기준).
     */
    @Column(name = "initial_investment", nullable = false, precision = 20, scale = 4)
    private BigDecimal initialInvestment;

    /**
     * 순 기여 금액 (기준 통화 기준).
     * 초기 투자금 + 추가 입금 - 출금액 합계.
     */
    @Column(name = "net_contribution", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal netContribution = BigDecimal.ZERO;

    /**
     * 포트폴리오 기준 통화 (KRW, USD 등).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", nullable = false, length = 10)
    @Builder.Default
    private Currency baseCurrency = Currency.KRW;

    /**
     * 실행 모드 (LIVE, DEMO).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    @Builder.Default
    private PriceMode priceMode = PriceMode.LIVE;

    /**
     * 낙관적 락을 위한 버전 관리.
     * 동시 수정 방지.
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PortfolioAsset> assets = new ArrayList<>();

    /**
     * 자산 추가.
     */
    public void addAsset(PortfolioAsset asset) {
        this.assets.add(asset);
        asset.assignPortfolio(this);
    }

    /**
     * 입금/출금에 따른 순 기여 금액 업데이트.
     * @param amount 입금액(+), 출금액(-)
     */
    public void updateContribution(BigDecimal amount) {
        this.netContribution = this.netContribution.add(amount);
    }

    /**
     * 포트폴리오 정보 수정.
     */
    public void update(String name, BigDecimal initialInvestment, Currency baseCurrency) {
        this.name = name;
        this.initialInvestment = initialInvestment;
        this.baseCurrency = baseCurrency;
    }
}
