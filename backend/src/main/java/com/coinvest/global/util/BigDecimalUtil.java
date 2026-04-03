package com.coinvest.global.util;

import com.coinvest.fx.domain.Currency;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 전역 부동소수점 처리 유틸리티.
 * 시스템 전체의 정밀도 및 반올림 정책을 통일함.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigDecimalUtil {

    private static final int KRW_SCALE = 0;
    private static final int USD_SCALE = 2;
    private static final int FX_RATE_SCALE = 6;
    private static final int COIN_SCALE = 8;
    private static final int WEIGHT_SCALE = 4;

    /**
     * 원화 잔고 및 수수료 정책 (소수점 이하 버림).
     * 예: 12.5원 -> 12원
     * @deprecated 다중 통화 구조 도입에 따라 formatAmount(value, Currency) 사용 권장
     */
    @Deprecated
    public static BigDecimal formatKrw(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(KRW_SCALE, RoundingMode.DOWN);
    }

    /**
     * 통화별 잔고 포맷팅 (KRW는 소수점 0자리, USD는 2자리).
     */
    public static BigDecimal formatAmount(BigDecimal value, Currency currency) {
        if (value == null) return BigDecimal.ZERO;
        if (currency == Currency.KRW) {
            return value.setScale(KRW_SCALE, RoundingMode.DOWN);
        }
        return value.setScale(USD_SCALE, RoundingMode.DOWN);
    }

    /**
     * 환율 포맷팅 (소수점 6자리).
     */
    public static BigDecimal formatExchangeRate(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(FX_RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 코인 수량 및 평단가 정책 (소수점 8자리 유지, 9자리에서 반올림).
     */
    public static BigDecimal formatCoin(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(COIN_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 리밸런싱 비중 및 편차 정책 (소수점 4자리 유지).
     */
    public static BigDecimal formatWeight(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
    }
}
