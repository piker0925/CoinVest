package com.coinvest.portfolio.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 포트폴리오 실시간 가치 평가 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioValuationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PortfolioRepository portfolioRepository;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final VirtualAccountRepository virtualAccountRepository;

    @Transactional(readOnly = true)
    public PortfolioValuation evaluate(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;
        return evaluate(portfolioId, portfolio.getBaseCurrency());
    }

    @Transactional(readOnly = true)
    public PortfolioValuation evaluate(Long portfolioId, Currency baseCurrency) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;

        PriceMode mode = PriceModeResolver.resolve(portfolio.getUser().getRole());
        List<PortfolioValuation.AssetValuation> assetValuations = new ArrayList<>();
        BigDecimal totalAssetValueBase = BigDecimal.ZERO;
        boolean isStaleExchangeRate = false;

        // 1. 자산 가격 배치 조회
        List<String> universalCodes = portfolio.getAssets().stream()
                .map(PortfolioAsset::getUniversalCode)
                .collect(Collectors.toList());
        Map<String, BigDecimal> priceMap = priceService.getPrices(universalCodes, mode);

        // 2. 환율 사전 로드 (Prefetch)
        Set<Currency> requiredCurrencies = new HashSet<>();
        portfolio.getAssets().forEach(a -> requiredCurrencies.add(a.getCurrency()));
        
        VirtualAccount account = virtualAccountRepository.findByUserId(portfolio.getUser().getId()).orElse(null);
        if (account != null) {
            account.getBalances().forEach(b -> requiredCurrencies.add(b.getCurrency()));
        }

        Map<Currency, ExchangeRateService.ExchangeRateResponse> fxMap = new HashMap<>();
        for (Currency currency : requiredCurrencies) {
            ExchangeRateService.ExchangeRateResponse fxResponse = exchangeRateService.getExchangeRateWithStatus(currency, baseCurrency, mode);
            fxMap.put(currency, fxResponse);
            if (fxResponse.isStale()) isStaleExchangeRate = true;
        }

        // 3. 개별 자산 가치 평가
        for (PortfolioAsset asset : portfolio.getAssets()) {
            BigDecimal currentPrice = priceMap.getOrDefault(asset.getUniversalCode(), BigDecimal.ZERO);
            BigDecimal evaluationNative = asset.getQuantity().multiply(currentPrice);
            
            BigDecimal fxRate = fxMap.get(asset.getCurrency()).rate();
            BigDecimal evaluationBase = evaluationNative.multiply(fxRate);
            
            totalAssetValueBase = totalAssetValueBase.add(evaluationBase);
            
            assetValuations.add(PortfolioValuation.AssetValuation.builder()
                    .universalCode(asset.getUniversalCode())
                    .currentPrice(currentPrice)
                    .quantity(asset.getQuantity())
                    .evaluationNative(evaluationNative)
                    .evaluationBase(evaluationBase)
                    .fxRate(fxRate)
                    .quoteCurrency(asset.getCurrency())
                    .targetWeight(asset.getTargetWeight())
                    .build());
        }

        BigDecimal totalBuyingPowerBase = BigDecimal.ZERO;
        if (account != null) {
            for (Balance balance : account.getBalances()) {
                BigDecimal buyingPowerNative = balance.getAvailableForPurchase();
                BigDecimal fxRate = fxMap.get(balance.getCurrency()).rate();
                totalBuyingPowerBase = totalBuyingPowerBase.add(buyingPowerNative.multiply(fxRate));
            }
        }

        if (totalAssetValueBase.compareTo(BigDecimal.ZERO) > 0) {
            for (PortfolioValuation.AssetValuation av : assetValuations) {
                BigDecimal weight = av.getEvaluationBase()
                        .divide(totalAssetValueBase, 4, RoundingMode.HALF_UP);
                av.setCurrentWeight(weight);
            }
        } else {
            assetValuations.forEach(av -> av.setCurrentWeight(BigDecimal.ZERO));
        }

        PortfolioValuation result = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationBase(totalAssetValueBase)
                .buyingPowerBase(totalBuyingPowerBase)
                .baseCurrency(baseCurrency)
                .isStaleExchangeRate(isStaleExchangeRate)
                .assetValuations(assetValuations)
                .build();

        String key = RedisKeyConstants.getPortfolioValuationKey(mode, portfolioId);
        redisTemplate.opsForValue().set(key, result);

        return result;
    }
}
