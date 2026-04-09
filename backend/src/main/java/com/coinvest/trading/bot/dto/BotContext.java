package com.coinvest.trading.bot.dto;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.bot.domain.TradingBot;

import java.util.List;

/**
 * 봇 전략 실행 컨텍스트.
 * TradingBotExecutor가 구성하여 BotSignalStrategy.decide()에 전달.
 */
public record BotContext(
        TradingBot bot,
        List<String> targetAssets,
        PriceMode mode
) {}
