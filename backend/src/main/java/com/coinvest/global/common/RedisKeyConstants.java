package com.coinvest.global.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Redis Key 명명 규칙 정의.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyConstants {

    public static final String TICKER_PRICE_KEY = "price:ticker:%s";
    public static final String PRICE_TICKER_CHANNEL = "price:ticker:events";
    public static final String EXCHANGE_RATE_KEY = "price:fx:%s";
    public static final String PORTFOLIO_ASSET_MAPPING_KEY = "portfolio:asset:%s";
    public static final String PORTFOLIO_VALUATION_KEY = "portfolio:valuation:%d";
    public static final String ALERT_COOLDOWN_KEY = "alert:cooldown:%d:%s";
    public static final String ALERT_DAILY_COUNT_KEY = "alert:daily-count:%d";
    public static final String LOGIN_FAIL_COUNT_KEY = "auth:login-fail:%s";
    public static final String JWT_BLACKLIST_KEY = "auth:blacklist:%s";

    public static String format(String format, Object... args) {
        return String.format(format, args);
    }
}
