package com.coinvest.price.dto;

/**
 * 5분봉 캔들 데이터 DTO.
 * lightweight-charts 형식에 맞게 Unix 초 단위 timestamp 사용.
 */
public record CandleData(
        long time,   // Unix timestamp (seconds)
        double open,
        double high,
        double low,
        double close
) {}
