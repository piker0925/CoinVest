package com.coinvest.asset.repository;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByUniversalCode(String universalCode);
    Optional<Asset> findByExternalCode(String externalCode);
    List<Asset> findAllByAssetClass(AssetClass assetClass);
    List<Asset> findAllByIsDemo(boolean isDemo);
}
