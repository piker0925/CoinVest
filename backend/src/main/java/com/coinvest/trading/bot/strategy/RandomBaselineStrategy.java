package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import com.coinvest.trading.domain.OrderSide;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
public class RandomBaselineStrategy implements BotTradingStrategy {

    private final Random random = new Random();

    @Override
    public BotStrategyType getStrategyType() {
        return BotStrategyType.RANDOM_BASELINE;
    }

    @Override
    public List<OrderDecision> decide(BotTradingContext context) {
        List<OrderDecision> decisions = new ArrayList<>();

        if (context.priceWindows() == null) {
            return decisions;
        }

        // 제공된 모든 자산에 대해 랜덤하게 매수/매도 판별
        context.priceWindows().keySet().forEach(assetCode -> {
            boolean isBuy = random.nextBoolean();
            OrderSide side = isBuy ? OrderSide.BUY : OrderSide.SELL;
            
            // 시뮬레이션을 위한 고정 소액 매매 (실제로는 잔고/포지션에 따라 Executor에서 수량 검증됨)
            BigDecimal quantity = new BigDecimal("0.05");

            decisions.add(new OrderDecision(
                    assetCode,
                    side,
                    quantity,
                    "Random Baseline Signal: " + side.name()
            ));
        });

        return decisions;
    }
}
