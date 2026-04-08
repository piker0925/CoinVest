package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioUpdatedEvent;
import com.coinvest.price.dto.TickerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 전역 자산 구독 및 주기적 가격 폴링 관리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSubscriptionService {

    private final UpbitPriceProvider upbitPriceProvider;
    private final PriceProviderRouter priceProviderRouter;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void initSubscriptions() {
        refreshSubscriptions();
    }

    @EventListener
    public void onPortfolioUpdated(PortfolioUpdatedEvent event) {
        log.info("Received portfolio update event. Refreshing subscriptions...");
        refreshSubscriptions();
    }

    /**
     * 실시간 구독 및 폴링 대상 갱신.
     */
    public void refreshSubscriptions() {
        List<String> universalCodes = portfolioRepository.findAll().stream()
                .flatMap(p -> p.getAssets().stream())
                .map(PortfolioAsset::getUniversalCode)
                .distinct()
                .collect(Collectors.toList());

        // isDemo = false인 실제 자산만 필터링하여 구독 요청
        List<Asset> assets = assetRepository.findAll().stream()
                .filter(a -> universalCodes.contains(a.getUniversalCode()))
                .filter(a -> !a.isDemo())
                .collect(Collectors.toList());

        // 1. Upbit WebSocket 구독 갱신 (CRYPTO 전용)
        List<Asset> cryptoAssets = assets.stream()
                .filter(a -> a.getAssetClass() == AssetClass.CRYPTO)
                .collect(Collectors.toList());
        upbitPriceProvider.subscribe(cryptoAssets);

        log.info("Refreshed price subscriptions for {} real assets ({} crypto)", assets.size(), cryptoAssets.size());
    }

    /**
     * 5분 주기로 폴링 대상 자산 가격 갱신 (KR_STOCK, US_STOCK 등).
     */
    @Scheduled(fixedRate = 300000)
    public void pollPrices() {
        List<String> universalCodes = portfolioRepository.findAll().stream()
                .flatMap(p -> p.getAssets().stream())
                .map(PortfolioAsset::getUniversalCode)
                .distinct()
                .collect(Collectors.toList());

        // isDemo = false인 실제 자산만 폴링
        List<Asset> assetsToPoll = assetRepository.findAll().stream()
                .filter(a -> universalCodes.contains(a.getUniversalCode()))
                .filter(a -> !a.isDemo())
                .filter(a -> a.getAssetClass() != AssetClass.CRYPTO)
                .collect(Collectors.toList());

        if (assetsToPoll.isEmpty()) return;

        log.info("Polling prices for {} real assets", assetsToPoll.size());
        List<TickerEvent> events = priceProviderRouter.fetchPrices(assetsToPoll);

        for (TickerEvent event : events) {
            redisTemplate.convertAndSend(RedisKeyConstants.getPriceTickerChannel(PriceMode.LIVE), event);
        }
    }
}
