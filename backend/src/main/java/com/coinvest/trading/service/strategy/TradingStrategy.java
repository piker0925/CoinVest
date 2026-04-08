package com.coinvest.trading.service.strategy;

import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.Position;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 모드별(DEMO, LIVE) 거래 전략 인터페이스.
 * 가격 조회, 통합증거금 환율, 포지션 관리 등 모드에 따라 달라지는 로직을 캡슐화함.
 */
public interface TradingStrategy {

    /**
     * 해당 전략이 지원하는 실행 모드 반환.
     */
    PriceMode getMode();

    /**
     * 모드에 맞는 현재가 조회.
     */
    BigDecimal getCurrentPrice(String universalCode);

    /**
     * 모드에 맞는 환율 조회.
     */
    BigDecimal getExchangeRate(Currency base, Currency quote);

    /**
     * 모드에 맞는 사용자 포지션 조회.
     */
    Optional<Position> getPosition(Long userId, String universalCode);

    /**
     * 모드에 맞는 Redis ZSet에 지정가 주문 등록.
     */
    void registerLimitOrder(Order order);
}
