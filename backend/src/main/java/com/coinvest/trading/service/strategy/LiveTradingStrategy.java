package com.coinvest.trading.service.strategy;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.Position;
import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.repository.BalanceRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 실데이터(LIVE) 기반 거래 전략 구현체.
 */
@Component
@RequiredArgsConstructor
public class LiveTradingStrategy implements TradingStrategy {

    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final PositionRepository positionRepository;
    private final BalanceRepository balanceRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public PriceMode getMode() {
        return PriceMode.LIVE;
    }

    @Override
    public BigDecimal getCurrentPrice(String universalCode) {
        return priceService.getCurrentPrice(universalCode, PriceMode.LIVE);
    }

    @Override
    public BigDecimal getExchangeRate(Currency base, Currency quote) {
        return exchangeRateService.getCurrentExchangeRate(base, quote, PriceMode.LIVE);
    }

    @Override
    public Optional<Position> getPosition(Long userId, String universalCode) {
        return positionRepository.findByUserIdAndUniversalCodeAndPriceMode(userId, universalCode, PriceMode.LIVE);
    }

    @Override
    public void registerLimitOrder(Order order) {
        String side = order.getSide().name().toLowerCase();
        String redisKey = RedisKeyConstants.getLimitOrderKey(PriceMode.LIVE, side, order.getUniversalCode());
        redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), order.getPrice().doubleValue());
    }

    @Override
    public void settle(Settlement settlement) {
        Long accountId = virtualAccountRepository.findByUserId(settlement.getUser().getId())
                .orElseThrow().getId();
        
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(accountId, settlement.getCurrency())
                .orElseThrow();

        // 실거래 정산금 반영 (미정산금 감소, 가용잔고 증가)
        balance.decreaseUnsettled(settlement.getAmount());
        balance.increaseAvailable(settlement.getAmount());
        
        balanceRepository.save(balance);
    }
}
