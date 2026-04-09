package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MomentumStrategyTest {

    private final MomentumStrategy strategy = new MomentumStrategy();

    @Test
    @DisplayName("전략 타입 반환 검증")
    void test_strategy_type() {
        assertThat(strategy.getStrategyType()).isEqualTo(BotStrategyType.MOMENTUM);
    }

    @Test
    @DisplayName("데이터 부족(26개 미만) 시 빈 리스트를 반환해야 함")
    void test_decide_not_enough_data() {
        // 25개의 데이터만 생성
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            prices.add(new BigDecimal("100"));
        }

        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("단기 이평선 > 장기 이평선일 경우 상승 추세로 판단하여 BUY 반환")
    void test_decide_uptrend_buy() {
        // 26개의 데이터 중 앞쪽(오래된 데이터)은 낮게, 뒤쪽(최신 데이터)은 높게 설정
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 14; i++) { // 과거 14개 데이터: 100
            prices.add(new BigDecimal("100"));
        }
        for (int i = 0; i < 12; i++) { // 최근 12개 데이터: 200 (SMA12 = 200, SMA26 = (100*14 + 200*12)/26 = 146.15)
            prices.add(new BigDecimal("200"));
        }

        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).hasSize(1);
        OrderDecision decision = decisions.get(0);
        assertThat(decision.side()).isEqualTo(OrderSide.BUY);
        assertThat(decision.reason()).contains("Uptrend");
    }

    @Test
    @DisplayName("단기 이평선 < 장기 이평선일 경우 하락 추세로 판단하여 SELL 반환 (포지션 보유 시)")
    void test_decide_downtrend_sell_with_position() {
        // 앞쪽(오래된 데이터)은 높게, 뒤쪽(최신 데이터)은 낮게 설정
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 14; i++) { // 과거 14개 데이터: 200
            prices.add(new BigDecimal("200"));
        }
        for (int i = 0; i < 12; i++) { // 최근 12개 데이터: 100 (SMA12 = 100, SMA26 = (200*14 + 100*12)/26 = 153.84)
            prices.add(new BigDecimal("100"));
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
        assertThat(decision.reason()).contains("Downtrend");
    }

    @Test
    @DisplayName("상승 추세이나 이미 포지션을 보유 중인 경우 매수 보류 (Over-trading 방어)")
    void test_decide_uptrend_buy_skipped_due_to_existing_position() {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 14; i++) prices.add(new BigDecimal("100"));
        for (int i = 0; i < 12; i++) prices.add(new BigDecimal("200"));

        Position position = Position.builder()
                .universalCode("BTC")
                .quantity(new BigDecimal("1"))
                .build();

        BotTradingContext context = new BotTradingContext(1L, null, List.of(position), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("하락 추세이나 포지션이 없는 경우 매도 보류 (Short selling 방어)")
    void test_decide_downtrend_sell_skipped_due_to_no_position() {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 14; i++) prices.add(new BigDecimal("200"));
        for (int i = 0; i < 12; i++) prices.add(new BigDecimal("100"));

        // 빈 포지션 리스트 전달
        BotTradingContext context = new BotTradingContext(1L, null, Collections.emptyList(), Map.of("BTC", prices));
        List<OrderDecision> decisions = strategy.decide(context);

        assertThat(decisions).isEmpty();
    }
}
