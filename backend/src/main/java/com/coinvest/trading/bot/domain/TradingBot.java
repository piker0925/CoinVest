package com.coinvest.trading.bot.domain;

import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 자동 매매 봇 설정 엔티티.
 * userId 1:1로 봇 전용 VirtualAccount를 소유하며,
 * TradingService.createOrder()를 통해 일반 거래 파이프라인을 그대로 사용함.
 */
@Entity
@Table(name = "trading_bots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingBot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false)
    private BotStrategyType strategyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceMode priceMode;

    /**
     * 전략별 파라미터 (JSON).
     * 공통 필드: targetAssets (List<String>)
     * MOMENTUM: ma_period (Integer)
     * RSI: rsi_period (Integer), overbought (Integer), oversold (Integer)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Builder
    private TradingBot(Long userId, BotStrategyType strategyType, BotStatus status,
                       PriceMode priceMode, Map<String, Object> config) {
        this.userId = userId;
        this.strategyType = strategyType;
        this.status = status;
        this.priceMode = priceMode;
        this.config = config;
    }

    public void pause() {
        this.status = BotStatus.PAUSED;
    }

    public void resume() {
        this.status = BotStatus.ACTIVE;
    }

    public void updateConfig(Map<String, Object> config) {
        this.config = config;
    }
}
