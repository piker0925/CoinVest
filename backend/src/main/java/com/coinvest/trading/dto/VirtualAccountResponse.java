package com.coinvest.trading.dto;

import com.coinvest.fx.domain.Currency;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.VirtualAccount;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 가상 계좌 응답 DTO.
 */
public record VirtualAccountResponse(
    BigDecimal totalAssetsKrw, // (총 잔고 합산 + 자산 평가액 합산) - 기본 KRW 환산 기준
    List<BalanceResponse> balances
) {
    public record BalanceResponse(
        Currency currency,
        BigDecimal available,
        BigDecimal locked,
        BigDecimal total
    ) {
        public static BalanceResponse from(Balance balance) {
            return new BalanceResponse(
                balance.getCurrency(),
                balance.getAvailable(),
                balance.getLocked(),
                balance.getAvailable().add(balance.getLocked())
            );
        }
    }

    public static VirtualAccountResponse of(VirtualAccount account, BigDecimal totalBalanceKrw, BigDecimal totalEvalKrw) {
        List<BalanceResponse> balanceResponses = account.getBalances().stream()
                .map(BalanceResponse::from)
                .collect(Collectors.toList());

        return new VirtualAccountResponse(
            totalBalanceKrw.add(totalEvalKrw),
            balanceResponses
        );
    }
}
