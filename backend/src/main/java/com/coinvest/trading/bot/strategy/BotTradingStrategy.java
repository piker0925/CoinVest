package com.coinvest.trading.bot.strategy;

import com.coinvest.trading.bot.domain.BotStrategyType;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;

import java.util.List;

/**
 * 봇 매매 전략 인터페이스 (Strategy Pattern).
 * OCP(개방-폐쇄 원칙)를 준수하며 새로운 알고리즘을 추가할 수 있음.
 */
public interface BotTradingStrategy {
    
    /**
     * 전략 식별 타입 반환.
     */
    BotStrategyType getStrategyType();

    /**
     * 현재 컨텍스트(계좌 상태, 최근 가격 윈도우)를 기반으로 매매 결정.
     */
    List<OrderDecision> decide(BotTradingContext context);
}
