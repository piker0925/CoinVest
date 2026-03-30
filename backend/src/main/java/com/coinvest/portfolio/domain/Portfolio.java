package com.coinvest.portfolio.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.BaseEntity;
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
     * 초기 투자 금액 (KRW).
     */
    @Column(name = "initial_investment_krw", nullable = false, precision = 20, scale = 4)
    private BigDecimal initialInvestmentKrw;

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
     * 포트폴리오 정보 수정.
     */
    public void update(String name, BigDecimal initialInvestmentKrw) {
        this.name = name;
        this.initialInvestmentKrw = initialInvestmentKrw;
    }
}
