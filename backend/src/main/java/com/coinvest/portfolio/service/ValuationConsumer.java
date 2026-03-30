package com.coinvest.portfolio.service;

import com.coinvest.global.common.KafkaTopicConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 분산된 포트폴리오 평가 요청을 처리하는 컨슈머.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationConsumer {

    private final PortfolioValuationService valuationService;

    /**
     * 특정 포트폴리오 ID에 대해 평가 수행.
     */
    @KafkaListener(
            topics = KafkaTopicConstants.EVALUATE_PORTFOLIO,
            groupId = "valuation-worker-service",
            concurrency = "3" // 병렬 워커 설정
    )
    public void onEvaluatePortfolio(Long portfolioId) {
        if (log.isTraceEnabled()) {
            log.trace("Received valuation request for portfolio: {}", portfolioId);
        }
        valuationService.evaluate(portfolioId);
    }
}
