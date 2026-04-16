package com.coinvest.price.event;

import com.coinvest.global.common.PriceMode;
import com.coinvest.price.dto.TickerEvent;

/**
 * 가격 이벤트. PriceMode를 포함하여 Demo/Live 스탑로스·테이크프로핏 매칭이
 * 올바른 모드의 주문만 처리하도록 격리 보장.
 */
public record TickerUpdatedEvent(TickerEvent ticker, PriceMode mode) {
}
