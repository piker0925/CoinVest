package com.coinvest.portfolio.domain;

import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 포트폴리오 가치 스냅샷.
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

    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate;

    @Column(name = "total_evaluation_base", nullable = false, precision = 38, scale = 20)
    private BigDecimal totalEvaluationBase; // 필드명 수정: totalValueBase -> totalEvaluationBase

    @Column(name = "net_contribution", nullable = false, precision = 38, scale = 20)
    private BigDecimal netContribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    private PriceMode priceMode;
}
