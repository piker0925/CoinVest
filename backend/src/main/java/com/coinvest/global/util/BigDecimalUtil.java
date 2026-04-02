package com.coinvest.global.util;

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
    private static final int COIN_SCALE = 8;
    private static final int WEIGHT_SCALE = 4;

    /**
     * 원화 잔고 및 수수료 정책 (소수점 이하 버림).
     * 예: 12.5원 -> 12원
     */
    public static BigDecimal formatKrw(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(KRW_SCALE, RoundingMode.DOWN);
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
