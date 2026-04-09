package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.Position;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 평균 회귀 (Mean Reversion) 전략
 * RSI(14) 보조 지표를 활용하여 과매수(RSI > 70) 시 매도, 과매도(RSI < 30) 시 매수함.
 */
@Slf4j
@Component
public class MeanReversionStrategy implements BotTradingStrategy {

    private static final int RSI_PERIOD = 14;
    private static final BigDecimal RSI_OVERSOLD_THRESHOLD = new BigDecimal("30");
    private static final BigDecimal RSI_OVERBOUGHT_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal TRADE_QUANTITY = new BigDecimal("0.05");

    @Override
    public BotStrategyType getStrategyType() {
        return BotStrategyType.MEAN_REVERSION;
    }

    @Override
    public List<OrderDecision> decide(BotTradingContext context) {
        List<OrderDecision> decisions = new ArrayList<>();

        if (context.priceWindows() == null || context.priceWindows().isEmpty()) {
            return decisions;
        }

        List<Position> currentPositions = context.positions() != null ? context.positions() : new ArrayList<>();

        for (Map.Entry<String, List<BigDecimal>> entry : context.priceWindows().entrySet()) {
            String assetCode = entry.getKey();
            List<BigDecimal> prices = entry.getValue();

            // RSI 계산을 위해선 N+1개의 데이터가 필요함 (변화량을 구해야 하므로)
            if (prices == null || prices.size() < RSI_PERIOD + 1) {
                log.debug("Not enough price data for mean reversion strategy: {}", assetCode);
                continue;
            }

            BigDecimal rsi = calculateRsi(prices, RSI_PERIOD);

            if (rsi == null) {
                continue;
            }

            boolean hasPosition = currentPositions.stream()
                    .anyMatch(p -> p.getUniversalCode().equals(assetCode) && p.getQuantity().compareTo(BigDecimal.ZERO) > 0);

            // 과매도 (RSI < 30) : 반등 예상, 매수
            if (rsi.compareTo(RSI_OVERSOLD_THRESHOLD) < 0) {
                if (!hasPosition) {
                    decisions.add(new OrderDecision(
                            assetCode,
                            OrderSide.BUY,
                            TRADE_QUANTITY,
                            String.format("Oversold / Mean Reversion (RSI: %s < 30)", rsi)
                    ));
                } else {
                    log.debug("Already hold position for {}, skipping BUY (Over-trading prevention)", assetCode);
                }
            } 
            // 과매수 (RSI > 70) : 하락 예상, 매도
            else if (rsi.compareTo(RSI_OVERBOUGHT_THRESHOLD) > 0) {
                if (hasPosition) {
                    decisions.add(new OrderDecision(
                            assetCode,
                            OrderSide.SELL,
                            TRADE_QUANTITY,
                            String.format("Overbought / Mean Reversion (RSI: %s > 70)", rsi)
                    ));
                } else {
                    log.debug("No position for {}, skipping SELL (Short selling prevention)", assetCode);
                }
            }
        }

        return decisions;
    }

    /**
     * RSI(14) 계산.
     * RS = (14일간 상승폭 평균) / (14일간 하락폭 평균)
     * RSI = 100 - (100 / (1 + RS))
     */
    private BigDecimal calculateRsi(List<BigDecimal> prices, int period) {
        int startIndex = prices.size() - period - 1;
        
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (int i = startIndex; i < prices.size() - 1; i++) {
            BigDecimal current = prices.get(i);
            BigDecimal next = prices.get(i + 1);
            BigDecimal change = next.subtract(current);

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }

        BigDecimal avgGain = gains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100.00"); // 하락이 없으면 RSI 100
        }
        if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // 상승이 없으면 RSI 0
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        BigDecimal rsi = new BigDecimal("100").subtract(
                new BigDecimal("100").divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP)
        );

        return rsi.setScale(2, RoundingMode.HALF_UP);
    }
}
