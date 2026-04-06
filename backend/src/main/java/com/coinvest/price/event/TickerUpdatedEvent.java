package com.coinvest.price.event;

import com.coinvest.price.dto.TickerEvent;

public record TickerUpdatedEvent(TickerEvent ticker) {
}
