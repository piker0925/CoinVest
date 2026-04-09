package com.coinvest.trading.bot.dto;

import com.coinvest.trading.domain.Position;
import com.coinvest.trading.domain.VirtualAccount;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 봇 전략 판단을 위한 컨텍스트 DTO.
 * 현재 잔고, 보유 포지션, 최근 가격 윈도우 등을 포함.
 */
public record BotTradingContext(
    Long userId,
    VirtualAccount account,
    List<Position> positions,
    Map<String, List<BigDecimal>> priceWindows // Asset Code -> Price List (최근 60개)
) {
}
