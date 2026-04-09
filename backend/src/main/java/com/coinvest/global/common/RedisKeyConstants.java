package com.coinvest.global.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Redis Key 명명 규칙 정의.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyConstants {

    // --- Template Constants ---
    private static final String TICKER_PRICE_TEMPLATE = "price:ticker:%s";
    private static final String PRICE_TICKER_CHANNEL_TEMPLATE = "price:ticker:events";
    private static final String EXCHANGE_RATE_TEMPLATE = "price:fx:%s";
    private static final String PORTFOLIO_ASSET_MAPPING_TEMPLATE = "portfolio:asset:%s";
    private static final String PORTFOLIO_VALUATION_TEMPLATE = "portfolio:valuation:%d";
    private static final String ALERT_COOLDOWN_TEMPLATE = "alert:cooldown:%d:%s";
    private static final String ALERT_DAILY_COUNT_TEMPLATE = "alert:daily-count:%d";
    private static final String LIMIT_ORDER_TEMPLATE = "trading:limit-order:%s:%s";
    private static final String BENCHMARK_TEMPLATE = "benchmark:%s";
    private static final String BENCHMARK_HISTORY_TEMPLATE = "benchmark:history:%s";
    private static final String PRICE_WINDOW_TEMPLATE = "price:window:%s";
    private static final String PRICE_WINDOW_SLOT_TEMPLATE = "price:window:slot:%s";
    private static final String PORTFOLIO_SNAPSHOT_HISTORY_TEMPLATE = "portfolio:snapshot:%d";

    // --- Common ---
    public static final String LOGIN_FAIL_COUNT_KEY = "auth:login-fail:%s";
    public static final String JWT_BLACKLIST_KEY = "auth:blacklist:%s";

    // --- Mode-Specific Methods ---
    public static String getTickerPriceKey(PriceMode mode, String code) {
        return mode.getPrefixKey(String.format(TICKER_PRICE_TEMPLATE, code));
    }

    public static String getPriceTickerChannel(PriceMode mode) {
        return mode.getPrefixKey(PRICE_TICKER_CHANNEL_TEMPLATE);
    }

    public static String getExchangeRateKey(PriceMode mode, String currencyPair) {
        return mode.getPrefixKey(String.format(EXCHANGE_RATE_TEMPLATE, currencyPair));
    }

    public static String getPortfolioAssetMappingKey(PriceMode mode, String universalCode) {
        return mode.getPrefixKey(String.format(PORTFOLIO_ASSET_MAPPING_TEMPLATE, universalCode));
    }

    public static String getPortfolioValuationKey(PriceMode mode, Long portfolioId) {
        return mode.getPrefixKey(String.format(PORTFOLIO_VALUATION_TEMPLATE, portfolioId));
    }

    public static String getAlertCooldownKey(PriceMode mode, Long portfolioId, String type) {
        return mode.getPrefixKey(String.format(ALERT_COOLDOWN_TEMPLATE, portfolioId, type));
    }

    public static String getAlertDailyCountKey(PriceMode mode, Long portfolioId) {
        return mode.getPrefixKey(String.format(ALERT_DAILY_COUNT_TEMPLATE, portfolioId));
    }

    public static String getLimitOrderKey(PriceMode mode, String side, String universalCode) {
        return mode.getPrefixKey(String.format(LIMIT_ORDER_TEMPLATE, side.toLowerCase(), universalCode));
    }

    public static String getBenchmarkKey(PriceMode mode, String code) {
        return mode.getPrefixKey(String.format(BENCHMARK_TEMPLATE, code));
    }

    public static String getBenchmarkHistoryKey(PriceMode mode, String code) {
        return mode.getPrefixKey(String.format(BENCHMARK_HISTORY_TEMPLATE, code));
    }

    /**
     * 포트폴리오 일별 스냅샷 이력 (Redis ZSet).
     * Score: LocalDate.toEpochDay(), Value: JSON {totalValueBase, netContribution}
     * 90일 보존. BenchmarkService에서 기간별 수익률 계산 시 빠른 조회용.
     */
    public static String getPortfolioSnapshotHistoryKey(PriceMode mode, Long portfolioId) {
        return mode.getPrefixKey(String.format(PORTFOLIO_SNAPSHOT_HISTORY_TEMPLATE, portfolioId));
    }

    /**
     * 봇 전략 지표 계산용 가격 윈도우 (Redis LIST).
     * 최근 120개 5분봉 스냅샷 유지.
     */
    public static String getPriceWindowKey(PriceMode mode, String universalCode) {
        return mode.getPrefixKey(String.format(PRICE_WINDOW_TEMPLATE, universalCode));
    }

    /**
     * 봇 전략 가격 윈도우의 현재 진행 중인 5분 슬롯 Timestamp 캐시.
     */
    public static String getPriceWindowSlotKey(PriceMode mode, String universalCode) {
        return mode.getPrefixKey(String.format(PRICE_WINDOW_SLOT_TEMPLATE, universalCode));
    }

    public static String format(String format, Object... args) {
        return String.format(format, args);
    }
}
