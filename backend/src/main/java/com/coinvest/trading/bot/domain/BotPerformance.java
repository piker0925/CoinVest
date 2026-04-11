package com.coinvest.trading.bot.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 봇의 일일 자산 성과 스냅샷.
 */
@Entity
@Table(name = "bot_performances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bot_id", "snapshot_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotPerformance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    private TradingBot bot;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate; // LocalDate -> LocalDateTime (V1 Baselining 통일)

    @Column(name = "total_asset_value", nullable = false, precision = 38, scale = 20)
    private BigDecimal totalAssetValue;

    @Column(name = "daily_return_rate", nullable = false, precision = 38, scale = 20)
    private BigDecimal dailyReturnRate;

    @Column(name = "net_contribution", nullable = false, precision = 38, scale = 20)
    private BigDecimal netContribution;

    @Builder
    private BotPerformance(TradingBot bot, LocalDateTime snapshotDate, BigDecimal totalAssetValue,
                           BigDecimal dailyReturnRate, BigDecimal netContribution) {
        this.bot = bot;
        this.snapshotDate = snapshotDate;
        this.totalAssetValue = totalAssetValue;
        this.dailyReturnRate = dailyReturnRate;
        this.netContribution = netContribution;
    }
}
