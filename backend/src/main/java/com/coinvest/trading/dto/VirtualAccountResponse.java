package com.coinvest.trading.dto;

import com.coinvest.trading.domain.VirtualAccount;

import java.math.BigDecimal;
import java.util.List;

public record VirtualAccountResponse(
    Long id,
    BigDecimal availableBalance,
    BigDecimal lockedKrw,
    BigDecimal totalBalanceKrw,
    BigDecimal totalEvaluationAmount,
    BigDecimal totalAssets,
    BigDecimal totalReturnRate
) {
    public static VirtualAccountResponse of(VirtualAccount account, List<PositionResponse> positions, BigDecimal initialFund) {
        BigDecimal totalEval = positions.stream()
                .map(PositionResponse::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAssets = account.getBalanceKrw().add(totalEval);
        
        BigDecimal totalReturnRate = BigDecimal.ZERO;
        if (initialFund.compareTo(BigDecimal.ZERO) > 0) {
            totalReturnRate = totalAssets.subtract(initialFund)
                    .divide(initialFund, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        return new VirtualAccountResponse(
            account.getId(),
            account.getAvailableBalance(),
            account.getLockedKrw(),
            account.getBalanceKrw(),
            totalEval,
            totalAssets,
            totalReturnRate
        );
    }
}
