package com.coinvest.global.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Redis Key 명명 규칙 정의.
 * 형식: {domain}:{sub-domain}:{identifier}
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyConstants {

    /**
     * 실시간 코인 가격 (Ticker).
     * 예: price:ticker:CRYPTO:BTC
     */
    public static final String TICKER_PRICE_KEY = "price:ticker:%s";

    /**
     * 포트폴리오 자산 매핑 (특정 코인을 보유한 포트폴리오 목록).
     * 예: portfolio:asset:CRYPTO:BTC -> [1, 5, 10] (Portfolio IDs)
     */
    public static final String PORTFOLIO_ASSET_MAPPING_KEY = "portfolio:asset:%s";

    /**
     * 포트폴리오 실시간 평가 가치.
     * 예: portfolio:valuation:1
     */
    public static final String PORTFOLIO_VALUATION_KEY = "portfolio:valuation:%d";

    /**
     * 알림 발송 쿨다운 (중복 알림 방지용).
     * 예: alert:cooldown:1:CRYPTO:BTC (포트폴리오 1번의 CRYPTO:BTC 알림 쿨다운)
     */
    public static final String ALERT_COOLDOWN_KEY = "alert:cooldown:%d:%s";

    /**
     * 일일 알림 발송 횟수 카운터.
     * 예: alert:daily-count:1
     */
    public static final String ALERT_DAILY_COUNT_KEY = "alert:daily-count:%d";

    /**
     * 로그인 실패 횟수 (Brute-force 방어).
     * 예: auth:login-fail:user@example.com
     */
    public static final String LOGIN_FAIL_COUNT_KEY = "auth:login-fail:%s";

    /**
     * 로그아웃된 AccessToken 블랙리스트.
     * 예: auth:blacklist:eyJhbGci...
     */
    public static final String JWT_BLACKLIST_KEY = "auth:blacklist:%s";

    /**
     * 헬퍼 메서드: 키 생성.
     */
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }
}
