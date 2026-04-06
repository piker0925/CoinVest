package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.price.dto.TickerEvent;

import java.util.List;

/**
 * 자산 가격 제공자 인터페이스.
 */
public interface PriceProvider {

    /**
     * 해당 자산 클래스를 지원하는지 확인.
     */
    boolean supports(AssetClass assetClass);

    /**
     * 자산 목록에 대한 현재 가격을 폴링하여 반환.
     */
    List<TickerEvent> fetchPrices(List<Asset> assets);

    /**
     * 실시간 가격 수집 시작 (WebSocket 등).
     * 기본적으로 아무것도 하지 않으며, 필요한 구현체에서 오버라이드.
     */
    default void start() {}

    /**
     * 실시간 가격 수집 중단.
     */
    default void stop() {}
}
