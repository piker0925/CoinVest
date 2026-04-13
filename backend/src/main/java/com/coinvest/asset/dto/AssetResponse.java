package com.coinvest.asset.dto;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.fx.domain.Currency;

import java.math.BigDecimal;

public record AssetResponse(
        Long id,
        String universalCode,
        String name,
        AssetClass assetClass,
        Currency quoteCurrency,
        BigDecimal feeRate,
        boolean isDemo
) {
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getUniversalCode(),
                asset.getName(),
                asset.getAssetClass(),
                asset.getQuoteCurrency(),
                asset.getFeeRate(),
                asset.isDemo()
        );
    }
}
