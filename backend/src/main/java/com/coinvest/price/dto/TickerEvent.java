package com.coinvest.price.dto;

import com.coinvest.asset.domain.AssetClass;
import com.coinvest.fx.domain.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 자산 가격 변동 이벤트 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TickerEvent {

    private String universalCode; // 표준 코드 (예: CRYPTO:BTC)
    private AssetClass assetClass; // 자산 분류
    private Currency quoteCurrency; // 거래 통화

    @Deprecated
    @JsonProperty("cd")
    private String marketCode; // 마켓 코드 (레거시 호환용)

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
