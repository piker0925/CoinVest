package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class IndexTrackingStrategy implements BotTradingStrategy {

    @Override
    public BotStrategyType getStrategyType() {
        return BotStrategyType.INDEX_TRACKING;
    }

    @Override
    public List<OrderDecision> decide(BotTradingContext context) {
        // TODO: 시가총액 비중 추종 로직 구현
        return Collections.emptyList();
    }
}
