package com.coinvest.portfolio.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.price.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * 포트폴리오의 기본 설정 통화로 평가 수행.
     */
    @Transactional(readOnly = true)
    public PortfolioValuation evaluate(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;
        return evaluate(portfolioId, portfolio.getBaseCurrency());
    }

    /**
     * 특정 기준 통화(baseCurrency)로 포트폴리오 가치 평가 수행.
     * 정책 1-A: 순수 자산 가치만 합산 (계좌 현금 제외).
     */
    @Transactional(readOnly = true)
    public PortfolioValuation evaluate(Long portfolioId, Currency baseCurrency) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;

        List<PortfolioValuation.AssetValuation> assetValuations = new ArrayList<>();
        BigDecimal totalEvaluationBase = BigDecimal.ZERO;
        boolean isStaleExchangeRate = false;

        // 1. 자산 가격 배치 조회 (성능 최적화)
        List<String> universalCodes = portfolio.getAssets().stream()
                .map(PortfolioAsset::getUniversalCode)
                .collect(Collectors.toList());
        Map<String, BigDecimal> priceMap = priceService.getPrices(universalCodes);

        // 2. 개별 자산 가치 평가 (Price * Quantity * ExchangeRate)
        for (PortfolioAsset asset : portfolio.getAssets()) {
            BigDecimal currentPrice = priceMap.getOrDefault(asset.getUniversalCode(), BigDecimal.ZERO);
            BigDecimal evaluationNative = asset.getQuantity().multiply(currentPrice);
            
            // 기준 통화로 환산 (자산 통화 -> 기준 통화)
            // 정책 2-B: Stale 환율 허용하되 플래그 표시
            ExchangeRateService.ExchangeRateResponse fxResponse = exchangeRateService.getExchangeRateWithStatus(asset.getCurrency(), baseCurrency);
            BigDecimal fxRate = fxResponse.rate();
            if (fxResponse.isStale()) isStaleExchangeRate = true;

            BigDecimal evaluationBase = evaluationNative.multiply(fxRate);
            
            totalEvaluationBase = totalEvaluationBase.add(evaluationBase);
            
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

        // 3. 비중(Weight) 계산 (0.0 ~ 1.0)
        // 정책 1-A: 자산 합계 기준으로만 비중 계산
        if (totalEvaluationBase.compareTo(BigDecimal.ZERO) > 0) {
            for (PortfolioValuation.AssetValuation av : assetValuations) {
                BigDecimal weight = av.getEvaluationBase()
                        .divide(totalEvaluationBase, 4, RoundingMode.HALF_UP);
                av.setCurrentWeight(weight);
            }
        } else {
            assetValuations.forEach(av -> av.setCurrentWeight(BigDecimal.ZERO));
        }

        PortfolioValuation result = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationBase(totalEvaluationBase)
                .baseCurrency(baseCurrency)
                .isStaleExchangeRate(isStaleExchangeRate)
                .assetValuations(assetValuations)
                .build();

        String key = RedisKeyConstants.format(RedisKeyConstants.PORTFOLIO_VALUATION_KEY, portfolioId);
        redisTemplate.opsForValue().set(key, result);

        return result;
    }
}
