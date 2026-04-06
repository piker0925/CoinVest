package com.coinvest.portfolio.service;

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

    /**
     * 단일 포트폴리오 가치 평가 수행.
     */
    @Transactional(readOnly = true)
    public PortfolioValuation evaluate(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;

        List<PortfolioValuation.AssetValuation> assetValuations = new ArrayList<>();
        BigDecimal totalEvaluationKrw = BigDecimal.ZERO;

        for (PortfolioAsset asset : portfolio.getAssets()) {
            BigDecimal currentPrice = priceService.getCurrentPrice(asset.getUniversalCode());
            BigDecimal evaluationKrw = asset.getQuantity().multiply(currentPrice);
            
            totalEvaluationKrw = totalEvaluationKrw.add(evaluationKrw);
            
            assetValuations.add(PortfolioValuation.AssetValuation.builder()
                    .universalCode(asset.getUniversalCode())
                    .currentPrice(currentPrice)
                    .quantity(asset.getQuantity())
                    .currentEvaluationKrw(evaluationKrw)
                    .targetWeight(asset.getTargetWeight())
                    .build());
        }

        if (totalEvaluationKrw.compareTo(BigDecimal.ZERO) > 0) {
            for (PortfolioValuation.AssetValuation av : assetValuations) {
                BigDecimal weight = av.getCurrentEvaluationKrw()
                        .divide(totalEvaluationKrw, 4, RoundingMode.HALF_UP);
                av.setCurrentWeight(weight);
            }
        } else {
            assetValuations.forEach(av -> av.setCurrentWeight(BigDecimal.ZERO));
        }

        PortfolioValuation result = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationKrw(totalEvaluationKrw)
                .assetValuations(assetValuations)
                .build();

        String key = RedisKeyConstants.format(RedisKeyConstants.PORTFOLIO_VALUATION_KEY, portfolioId);
        redisTemplate.opsForValue().set(key, result);

        return result;
    }
}
