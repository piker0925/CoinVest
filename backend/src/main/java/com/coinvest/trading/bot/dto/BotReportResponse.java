package com.coinvest.trading.bot.dto;

import com.coinvest.trading.bot.domain.BotStatistics;

import java.math.BigDecimal;

public record BotReportResponse(
        Long botId,
        String period,
        BigDecimal returnRate,
        BigDecimal mdd,
        BigDecimal winRate,
        Integer tradeCount,
        boolean insufficientData
) {
    public static BotReportResponse from(Long botId, String period, BotStatistics stat) {
        if (stat == null || stat.getReturnRate() == null) {
            return new BotReportResponse(botId, period, null, null, null, 0, true);
        }
        return new BotReportResponse(botId, period,
                stat.getReturnRate(), stat.getMdd(), stat.getWinRate(), stat.getTradeCount(), false);
    }
}
