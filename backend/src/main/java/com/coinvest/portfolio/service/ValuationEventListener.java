package com.coinvest.portfolio.service;

import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.event.PortfolioValuationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 포트폴리오 평가 요청 이벤트를 처리하는 리스너.
 * 기존 ValuationConsumer를 대체함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValuationEventListener {

    private final PortfolioValuationService valuationService;
    private final RebalancingService rebalancingService;

    /**
     * 특정 포트폴리오 ID에 대해 비동기로 평가 수행.
     */
    @Async
    @EventListener
    public void onEvaluatePortfolio(PortfolioValuationEvent event) {
        Long portfolioId = event.portfolioId();
        if (log.isTraceEnabled()) {
            log.trace("Received valuation event for portfolio: {}", portfolioId);
        }
        
        try {
            PortfolioValuation valuation = valuationService.evaluate(portfolioId);
            if (valuation != null) {
                rebalancingService.processAlertTrigger(valuation);
            }
        } catch (Exception e) {
            log.error("Failed to evaluate portfolio: {}", portfolioId, e);
        }
    }
}
