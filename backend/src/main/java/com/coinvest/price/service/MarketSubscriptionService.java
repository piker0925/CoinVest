package com.coinvest.price.service;

import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 포트폴리오 변화에 따라 WebSocket 구독 마켓을 동적으로 관리하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSubscriptionService {

    private final UpbitWebSocketClient webSocketClient;
    private final PortfolioRepository portfolioRepository;

    /**
     * 서버 시작 시 기존 포트폴리오 기반으로 구독 시작.
     */
    @PostConstruct
    public void initSubscriptions() {
        refreshSubscriptions();
    }

    /**
     * 포트폴리오 업데이트 이벤트 수신 시 구독 갱신.
     */
    @KafkaListener(topics = KafkaTopicConstants.PORTFOLIO_UPDATED, groupId = "subscription-service")
    public void onPortfolioUpdated(PortfolioUpdatedEvent event) {
        log.info("Received portfolio update event. Refreshing subscriptions...");
        refreshSubscriptions();
    }

    /**
     * DB에서 현재 모든 포트폴리오가 보유한 마켓 코드를 중복 없이 추출하여 구독함.
     */
    public void refreshSubscriptions() {
        List<String> allUniversalCodes = portfolioRepository.findAll().stream()
                .flatMap(p -> p.getAssets().stream())
                .map(PortfolioAsset::getUniversalCode)
                .distinct()
                .collect(Collectors.toList());

        log.info("Refreshing Upbit WebSocket subscriptions for {} markets: {}", allUniversalCodes.size(), allUniversalCodes);
        webSocketClient.subscribe(allUniversalCodes);
    }
}
