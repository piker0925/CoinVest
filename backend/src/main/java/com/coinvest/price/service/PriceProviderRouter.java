package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.price.dto.TickerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 자산 클래스에 따라 적절한 PriceProvider를 선택하여 가격을 조회하는 라우터.
 */
@Service
@RequiredArgsConstructor
public class PriceProviderRouter {

    private final List<PriceProvider> providers;

    /**
     * 자산 클래스별로 그룹화하여 각각의 Provider에게 가격 조회를 요청.
     */
    public List<TickerEvent> fetchPrices(List<Asset> assets) {
        Map<AssetClass, List<Asset>> groupedAssets = assets.stream()
                .collect(Collectors.groupingBy(Asset::getAssetClass));

        return groupedAssets.entrySet().stream()
                .map(entry -> getProvider(entry.getKey()).fetchPrices(entry.getValue()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * 특정 자산 클래스를 지원하는 Provider 반환.
     */
    public PriceProvider getProvider(AssetClass assetClass) {
        return providers.stream()
                .filter(p -> p.supports(assetClass))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No PriceProvider found for asset class: " + assetClass));
    }
}
