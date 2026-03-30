package com.coinvest.portfolio.service;

import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingByMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /**
     * 단일 포트폴리오 가치 평가 수행.
     */
    @Transactional(readOnly = true)
    public PortfolioValuation evaluate(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;

        List<PortfolioValuation.AssetValuation> assetValuations = new ArrayList<>();
        BigDecimal totalEvaluationKrw = BigDecimal.ZERO;

        // 1. 각 자산의 현재 가치 계산 및 총액 합산
        for (PortfolioAsset asset : portfolio.getAssets()) {
            BigDecimal currentPrice = getCurrentPrice(asset.getMarketCode());
            BigDecimal evaluationKrw = asset.getQuantity().multiply(currentPrice);
            
            totalEvaluationKrw = totalEvaluationKrw.add(evaluationKrw);
            
            assetValuations.add(PortfolioValuation.AssetValuation.builder()
                    .marketCode(asset.getMarketCode())
                    .currentPrice(currentPrice)
                    .quantity(asset.getQuantity())
                    .currentEvaluationKrw(evaluationKrw)
                    .targetWeight(asset.getTargetWeight())
                    .build());
        }

        // 2. 현재 비중 계산 (0으로 나누기 방지)
        if (totalEvaluationKrw.compareTo(BigDecimal.ZERO) > 0) {
            for (PortfolioValuation.AssetValuation av : assetValuations) {
                BigDecimal weight = av.getCurrentEvaluationKrw()
                        .divide(totalEvaluationKrw, 4, RoundingMode.HALF_UP);
                av.setCurrentWeight(weight);
            }
        } else {
            // 자산 가치가 0인 경우 비중도 0
            assetValuations.forEach(av -> av.setCurrentWeight(BigDecimal.ZERO));
        }

        PortfolioValuation result = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationKrw(totalEvaluationKrw)
                .assetValuations(assetValuations)
                .build();

        // 3. Redis 저장
        String key = RedisKeyConstants.format(RedisKeyConstants.PORTFOLIO_VALUATION_KEY, portfolioId);
        redisTemplate.opsForValue().set(key, result);

        return result;
    }

    private BigDecimal getCurrentPrice(String marketCode) {
        String key = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, marketCode);
        Object price = redisTemplate.opsForValue().get(key);
        return price != null ? new BigDecimal(price.toString()) : BigDecimal.ZERO;
    }
}
