package com.coinvest.global.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Kafka 토픽 명칭 정의.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopicConstants {

    /**
     * 실시간 코인 가격 업데이트 이벤트.
     */
    public static final String PRICE_TICKER_UPDATED = "price.ticker.updated";

    /**
     * 포트폴리오 리밸런싱 알림 트리거 이벤트.
     */
    public static final String ALERT_REBALANCE_TRIGGERED = "alert.rebalance.triggered";

    /**
     * 포트폴리오 자산 정보 수정 이벤트 (Redis 캐시 갱신용).
     */
    public static final String PORTFOLIO_UPDATED = "portfolio.updated";

    /**
     * 특정 포트폴리오에 대한 가치 평가 요청 이벤트.
     */
    public static final String EVALUATE_PORTFOLIO = "evaluate.portfolio";

    /**
     * 가상 매매 체결 이벤트.
     */
    public static final String TRADE_ORDER_FILLED = "trade.order.filled";
}
