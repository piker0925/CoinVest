package com.coinvest.price.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Upbit WebSocket Ticker 메시지 매핑 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TickerEvent {

    @JsonProperty("cd")
    private String marketCode; // 마켓 코드 (예: KRW-BTC)

    @JsonProperty("tp")
    private BigDecimal tradePrice; // 현재가

    @JsonProperty("atp")
    private BigDecimal accTradePrice; // 누적 거래대금

    @JsonProperty("atv")
    private BigDecimal accTradeVolume; // 누적 거래량

    @JsonProperty("tms")
    private Long timestamp; // 타임스탬프

    @JsonProperty("ttms")
    private Long tradeTimestamp; // 체결 타임스탬프
}
