package com.coinvest.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 실행 모드 (시뮬레이션 vs 실데이터)
 */
@Getter
@RequiredArgsConstructor
public enum PriceMode {
    DEMO("demo"),
    LIVE("live");

    private final String prefix;

    public String getPrefixKey(String key) {
        return prefix + ":" + key;
    }
}
