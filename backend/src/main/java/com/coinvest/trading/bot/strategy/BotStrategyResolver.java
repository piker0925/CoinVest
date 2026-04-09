package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BotStrategyType → BotTradingStrategy bean 매핑.
 * TradingStrategyResolver와 동일한 패턴.
 */
@Component
public class BotStrategyResolver {

    private final Map<BotStrategyType, BotTradingStrategy> strategyMap;

    public BotStrategyResolver(List<BotTradingStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(BotTradingStrategy::getStrategyType, s -> s));
    }

    public BotTradingStrategy resolve(BotStrategyType strategyType) {
        BotTradingStrategy strategy = strategyMap.get(strategyType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported bot strategy type: " + strategyType);
        }
        return strategy;
    }
}
