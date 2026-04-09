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
public class GridStrategy implements BotTradingStrategy {

    @Override
    public BotStrategyType getStrategyType() {
        return BotStrategyType.GRID;
    }

    @Override
    public List<OrderDecision> decide(BotTradingContext context) {
        // TODO: 일정 가격 밴드 내 분할 매매 로직 구현
        return Collections.emptyList();
    }
}
