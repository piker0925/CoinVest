package com.coinvest.portfolio.domain;

import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 포트폴리오 일별 가치 스냅샷.
 * 기간별 수익률(1M, 3M, ALL) 계산을 위한 시계열 데이터 저장.
 * portfolio_id + snapshot_date 복합 UNIQUE 제약으로 하루 1건만 유지.
 */
@Entity
@Table(
    name = "portfolio_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_portfolio_snapshot_date",
        columnNames = {"portfolio_id", "snapshot_date"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PortfolioSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    /**
     * 스냅샷 날짜.
     * Daily Job 실행 시점의 날짜(KST)로 저장.
     */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /**
     * 총자산 (자산 평가액 + 현금 잔고, 기준 통화 기준).
     * PortfolioValuationService.evaluate()의 totalEvaluationBase + buyingPowerBase.
     */
    @Column(name = "total_value_base", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalValueBase;

    /**
     * 스냅샷 시점의 순기여금.
     * 기간별 수익률 계산 시 중간 입출금 보정 분모로 사용.
     * 공식: return = (V_now - V_start - (NC_now - NC_start)) / V_start
     */
    @Column(name = "net_contribution", nullable = false, precision = 20, scale = 4)
    private BigDecimal netContribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    private PriceMode priceMode;
}
