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
 * 모멘텀 (추세 추종) 전략
 * 단기 이동평균(SMA 12)과 장기 이동평균(SMA 26)을 비교하여 추세를 판별함.
 */
@Slf4j
@Component
public class MomentumStrategy implements BotTradingStrategy {

    private static final int SHORT_PERIOD = 12;
    private static final int LONG_PERIOD = 26;
    private static final BigDecimal TRADE_QUANTITY = new BigDecimal("0.05");

    @Override
    public BotStrategyType getStrategyType() {
        return BotStrategyType.MOMENTUM;
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

            // 장기 이동평균을 구할 만큼 데이터가 쌓이지 않았다면 판단 보류
            if (prices == null || prices.size() < LONG_PERIOD) {
                log.debug("Not enough price data for momentum strategy: {}", assetCode);
                continue;
            }

            BigDecimal shortSma = calculateSma(prices, SHORT_PERIOD);
            BigDecimal longSma = calculateSma(prices, LONG_PERIOD);

            boolean hasPosition = currentPositions.stream()
                    .anyMatch(p -> p.getUniversalCode().equals(assetCode) && p.getQuantity().compareTo(BigDecimal.ZERO) > 0);

            // 단기 이평선 > 장기 이평선 : 상승 추세 (매수)
            if (shortSma.compareTo(longSma) > 0) {
                if (!hasPosition) {
                    decisions.add(new OrderDecision(
                            assetCode,
                            OrderSide.BUY,
                            TRADE_QUANTITY,
                            String.format("Golden Cross/Uptrend (SMA12:%s > SMA26:%s)", shortSma, longSma)
                    ));
                } else {
                    log.debug("Already hold position for {}, skipping BUY (Over-trading prevention)", assetCode);
                }
            } 
            // 단기 이평선 < 장기 이평선 : 하락 추세 (매도)
            else if (shortSma.compareTo(longSma) < 0) {
                if (hasPosition) {
                    decisions.add(new OrderDecision(
                            assetCode,
                            OrderSide.SELL,
                            TRADE_QUANTITY,
                            String.format("Dead Cross/Downtrend (SMA12:%s < SMA26:%s)", shortSma, longSma)
                    ));
                } else {
                    log.debug("No position for {}, skipping SELL (Short selling prevention)", assetCode);
                }
            }
        }

        return decisions;
    }

    /**
     * 최근 N개의 데이터로 단순 이동평균(SMA) 계산.
     * prices 리스트의 마지막 요소가 가장 최신 가격이라고 가정함.
     */
    private BigDecimal calculateSma(List<BigDecimal> prices, int period) {
        int startIndex = prices.size() - period;
        BigDecimal sum = BigDecimal.ZERO;
        
        for (int i = startIndex; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
}
