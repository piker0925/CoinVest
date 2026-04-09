package com.coinvest.trading.bot.dto;

import com.coinvest.trading.bot.domain.BotStatistics;
import com.coinvest.trading.bot.domain.BotStatus;
import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.domain.TradingBot;

import java.math.BigDecimal;

public record BotSummaryResponse(
        Long id,
        BotStrategyType strategyType,
        BotStatus status,
        BigDecimal returnRate1M,   // null = insufficient_data
        BigDecimal returnRate3M,
        BigDecimal returnRateAll
) {
    public static BotSummaryResponse of(TradingBot bot,
                                        BotStatistics stat1M,
                                        BotStatistics stat3M,
                                        BotStatistics statAll) {
        return new BotSummaryResponse(
                bot.getId(),
                bot.getStrategyType(),
                bot.getStatus(),
                stat1M != null ? stat1M.getReturnRate() : null,
                stat3M != null ? stat3M.getReturnRate() : null,
                statAll != null ? statAll.getReturnRate() : null
        );
    }
}
