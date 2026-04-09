package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import com.coinvest.trading.domain.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RandomBaselineStrategyTest {

    private final RandomBaselineStrategy strategy = new RandomBaselineStrategy();

    @Test
    @DisplayName("전략 타입 반환 검증: RANDOM_BASELINE 이어야 함")
    void test_strategy_type() {
        assertThat(strategy.getStrategyType()).isEqualTo(BotStrategyType.RANDOM_BASELINE);
    }

    @Test
    @DisplayName("빈 priceWindows를 받으면 빈 리스트를 반환해야 함")
    void test_decide_null_windows() {
        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), null);
        List<OrderDecision> decisions = strategy.decide(context);
        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("priceWindows에 존재하는 자산 수만큼 랜덤 매매 결정을 반환해야 함")
    void test_decide_random_decisions() {
        // given
        Map<String, List<BigDecimal>> windows = Map.of(
                "CRYPTO:BTC", List.of(BigDecimal.ONE),
                "US_STOCK:AAPL", List.of(BigDecimal.TEN)
        );
        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), windows);

        // when
        List<OrderDecision> decisions = strategy.decide(context);

        // then
        assertThat(decisions).hasSize(2);
        
        // 결과 객체 검증
        for (OrderDecision decision : decisions) {
            assertThat(decision.universalCode()).isIn("CRYPTO:BTC", "US_STOCK:AAPL");
            assertThat(decision.side()).isIn(OrderSide.BUY, OrderSide.SELL);
            assertThat(decision.quantity()).isEqualByComparingTo("0.05");
            assertThat(decision.reason()).startsWith("Random Baseline Signal: ");
        }
    }
}
