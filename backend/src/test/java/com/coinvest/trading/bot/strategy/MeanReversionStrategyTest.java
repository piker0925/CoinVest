package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeanReversionStrategyTest {

    private final MeanReversionStrategy strategy = new MeanReversionStrategy();

    @Test
    @DisplayName("전략 타입 반환 검증")
    void test_strategy_type() {
        assertThat(strategy.getStrategyType()).isEqualTo(BotStrategyType.MEAN_REVERSION);
    }

    @Test
    @DisplayName("데이터 부족(15개 미만) 시 빈 리스트 반환")
    void test_decide_not_enough_data() {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            prices.add(new BigDecimal("100"));
        }

        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("지속적인 상승으로 RSI > 70 이면 SELL 반환 (포지션 보유 시 과매수 매도)")
    void test_decide_overbought_sell_with_position() {
        List<BigDecimal> prices = new ArrayList<>();
        // 14일간 10씩 계속 오름 (하락폭=0 -> RSI=100)
        prices.add(new BigDecimal("100"));
        for (int i = 1; i <= 14; i++) {
            prices.add(new BigDecimal("100").add(new BigDecimal(i * 10)));
        }

        Position position = Position.builder()
                .universalCode("BTC")
                .quantity(new BigDecimal("1"))
                .build();

        BotTradingContext context = new BotTradingContext(1L, null, List.of(position), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).hasSize(1);
        OrderDecision decision = decisions.get(0);
        assertThat(decision.side()).isEqualTo(OrderSide.SELL);
        assertThat(decision.reason()).contains("Overbought");
    }

    @Test
    @DisplayName("지속적인 상승으로 RSI > 70 이나 포지션이 없으면 매도 보류 (Short selling 방어)")
    void test_decide_overbought_sell_skipped_due_to_no_position() {
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(new BigDecimal("100"));
        for (int i = 1; i <= 14; i++) {
            prices.add(new BigDecimal("100").add(new BigDecimal(i * 10)));
        }

        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("지속적인 하락으로 RSI < 30 이면 BUY 반환 (포지션 없을 시 과매도 매수)")
    void test_decide_oversold_buy_no_position() {
        List<BigDecimal> prices = new ArrayList<>();
        // 14일간 10씩 계속 내림 (상승폭=0 -> RSI=0)
        prices.add(new BigDecimal("300"));
        for (int i = 1; i <= 14; i++) {
            prices.add(new BigDecimal("300").subtract(new BigDecimal(i * 10)));
        }

        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).hasSize(1);
        OrderDecision decision = decisions.get(0);
        assertThat(decision.side()).isEqualTo(OrderSide.BUY);
        assertThat(decision.reason()).contains("Oversold");
    }

    @Test
    @DisplayName("지속적인 하락으로 RSI < 30 이나 이미 포지션을 보유 중인 경우 매수 보류 (Over-trading 방어)")
    void test_decide_oversold_buy_skipped_due_to_existing_position() {
        List<BigDecimal> prices = new ArrayList<>();
        prices.add(new BigDecimal("300"));
        for (int i = 1; i <= 14; i++) {
            prices.add(new BigDecimal("300").subtract(new BigDecimal(i * 10)));
        }

        Position position = Position.builder()
                .universalCode("BTC")
                .quantity(new BigDecimal("1"))
                .build();

        BotTradingContext context = new BotTradingContext(1L, null, List.of(position), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("횡보장(RSI 30~70 사이)이면 매매 보류(빈 리스트 반환)")
    void test_decide_hold() {
        List<BigDecimal> prices = new ArrayList<>();
        // 14일간 오르락 내리락 반복 (RSI 약 50)
        BigDecimal price = new BigDecimal("100");
        prices.add(price);
        for (int i = 0; i < 14; i++) {
            if (i % 2 == 0) {
                price = price.add(new BigDecimal("10"));
            } else {
                price = price.subtract(new BigDecimal("10"));
            }
            prices.add(price);
        }

        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }
}
