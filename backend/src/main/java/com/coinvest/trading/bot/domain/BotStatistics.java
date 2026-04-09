package com.coinvest.trading.bot.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 봇의 다기간 집계 통계 엔티티.
 * 배치 작업을 통해 주기적으로 갱신되며, API 조회 부하를 줄이기 위해 사용됨.
 */
@Entity
@Table(name = "bot_statistics", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bot_id", "period"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotStatistics extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    private TradingBot bot;

    @Column(nullable = false, length = 10)
    private String period; // '1M', '3M', 'ALL'

    @Column(name = "return_rate", precision = 38, scale = 20)
    private BigDecimal returnRate;

    @Column(precision = 38, scale = 20)
    private BigDecimal mdd;

    @Column(name = "sharpe_ratio", precision = 38, scale = 20)
    private BigDecimal sharpeRatio;

    @Column(name = "win_rate", precision = 38, scale = 20)
    private BigDecimal winRate;

    @Column(name = "trade_count", nullable = false)
    private Integer tradeCount = 0;

    @Builder
    private BotStatistics(TradingBot bot, String period, BigDecimal returnRate,
                          BigDecimal mdd, BigDecimal sharpeRatio, BigDecimal winRate,
                          Integer tradeCount) {
        this.bot = bot;
        this.period = period;
        this.returnRate = returnRate;
        this.mdd = mdd;
        this.sharpeRatio = sharpeRatio;
        this.winRate = winRate;
        this.tradeCount = tradeCount != null ? tradeCount : 0;
    }

    public void update(BigDecimal returnRate, BigDecimal mdd, BigDecimal sharpeRatio,
                       BigDecimal winRate, Integer tradeCount) {
        this.returnRate = returnRate;
        this.mdd = mdd;
        this.sharpeRatio = sharpeRatio;
        this.winRate = winRate;
        this.tradeCount = tradeCount;
    }
}
