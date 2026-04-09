package com.coinvest.trading.bot.dto;

import com.coinvest.trading.domain.OrderSide;

import java.math.BigDecimal;

/**
 * 봇 전략의 매매 의사결정 결과.
 * TradingBotExecutor가 수신하여 TradingService.createOrder()로 변환.
 */
public record OrderDecision(
        String universalCode,
        OrderSide side,
        BigDecimal quantity,
        String reason  // 로깅/추적용 근거 (DB 미저장)
) {}
