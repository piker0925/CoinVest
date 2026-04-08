package com.coinvest.trading.service.strategy;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.Position;
import com.coinvest.trading.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 시뮬레이션(DEMO) 기반 거래 전략 구현체.
 */
@Component
@RequiredArgsConstructor
public class DemoTradingStrategy implements TradingStrategy {

    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final PositionRepository positionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public PriceMode getMode() {
        return PriceMode.DEMO;
    }

    @Override
    public BigDecimal getCurrentPrice(String universalCode) {
        return priceService.getCurrentPrice(universalCode, PriceMode.DEMO);
    }

    @Override
    public BigDecimal getExchangeRate(Currency base, Currency quote) {
        return exchangeRateService.getCurrentExchangeRate(base, quote, PriceMode.DEMO);
    }

    @Override
    public Optional<Position> getPosition(Long userId, String universalCode) {
        return positionRepository.findByUserIdAndUniversalCodeAndPriceMode(userId, universalCode, PriceMode.DEMO);
    }

    @Override
    public void registerLimitOrder(Order order) {
        String side = order.getSide().name().toLowerCase();
        String redisKey = RedisKeyConstants.getLimitOrderKey(PriceMode.DEMO, side, order.getUniversalCode());
        redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), order.getPrice().doubleValue());
    }
}
