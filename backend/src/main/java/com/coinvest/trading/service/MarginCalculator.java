package com.coinvest.trading.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.trading.domain.Balance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarginCalculator {

    private final ExchangeRateService exchangeRateService;

    public BigDecimal calculateAndApplyMargin(Balance assetBalance, Balance otherBalance, BigDecimal requiredAmount) {
        BigDecimal availableTotal = assetBalance.getAvailableForPurchase();
        BigDecimal fxRate = exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW);

        if (availableTotal.compareTo(requiredAmount) < 0) {
            BigDecimal shortage = requiredAmount.subtract(availableTotal);
            BigDecimal requiredOtherCurrency;

            if (assetBalance.getCurrency() == Currency.USD) {
                // USD 부족 -> KRW에서 차감
                requiredOtherCurrency = shortage.multiply(fxRate).setScale(0, RoundingMode.UP);
            } else {
                // KRW 부족 -> USD에서 차감
                requiredOtherCurrency = shortage.divide(fxRate, 2, RoundingMode.UP);
            }

            if (otherBalance.getAvailableForPurchase().compareTo(requiredOtherCurrency) < 0) {
                throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
            }

            otherBalance.decreaseAvailable(requiredOtherCurrency);
            assetBalance.increaseAvailable(shortage);
            
            log.info("Integrated Margin Applied: Converted {} {} to {} {}", 
                    requiredOtherCurrency, otherBalance.getCurrency(), shortage, assetBalance.getCurrency());
        }
        
        assetBalance.lock(requiredAmount);
        return fxRate;
    }
}
