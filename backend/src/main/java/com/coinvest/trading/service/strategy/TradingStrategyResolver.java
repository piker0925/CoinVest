package com.coinvest.trading.service.strategy;

import com.coinvest.global.common.PriceMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PriceMode에 따라 적절한 TradingStrategy를 찾아주는 컴포넌트.
 */
@Component
public class TradingStrategyResolver {

    private final Map<PriceMode, TradingStrategy> strategyMap;

    public TradingStrategyResolver(List<TradingStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(TradingStrategy::getMode, s -> s));
    }

    /**
     * 실행 모드에 해당하는 전략 반환.
     */
    public TradingStrategy resolve(PriceMode mode) {
        TradingStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported price mode: " + mode);
        }
        return strategy;
    }
}
