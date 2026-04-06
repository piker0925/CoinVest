package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.OrderType;
import com.coinvest.trading.domain.Position;
import com.coinvest.trading.dto.OrderCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class OrderValidator {

    private final MarketHoursService marketHoursService;

    private static final BigDecimal MIN_ORDER_AMOUNT_KRW = new BigDecimal("5000");
    private static final BigDecimal MIN_ORDER_AMOUNT_USD = new BigDecimal("5");

    public void validateRequest(OrderCreateRequest request) {
        if (request.type() == OrderType.MARKET && request.price() != null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }
        if (request.type() == OrderType.LIMIT && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException(ErrorCode.TRADING_INVALID_ORDER_PRICE);
        }
    }

    public void validateMinOrderAmount(Currency currency, BigDecimal amount) {
        BigDecimal min = (currency == Currency.KRW) ? MIN_ORDER_AMOUNT_KRW : MIN_ORDER_AMOUNT_USD;
        if (amount.compareTo(min) < 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }
    }

    public void validateSellPosition(Position position, BigDecimal quantity) {
        if (position.getAvailableQuantity().compareTo(quantity) < 0) {
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY);
        }
    }
}
