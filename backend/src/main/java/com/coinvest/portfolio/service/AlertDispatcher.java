package com.coinvest.portfolio.service;

import com.coinvest.portfolio.domain.*;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.repository.AlertHistoryRepository;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.trading.service.SseEmitters;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송 디스패처.
 * 비동기 및 재시도 로직을 담당함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDispatcher {

    private final DiscordClient discordClient;
    private final AlertSettingRepository alertSettingRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final PortfolioRepository portfolioRepository;
    private final SseEmitters sseEmitters; // SSE 전송용 (기구축됨)

    /**
     * 리밸런싱 알림 발송 (비동기 + 재시도).
     */
    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    @Transactional
    public void dispatchRebalanceAlert(PortfolioValuation valuation) {
        AlertSetting setting = alertSettingRepository.findByPortfolioId(valuation.getPortfolioId())
                .orElseThrow();

        if (setting.getDiscordWebhookUrl() == null || !setting.isActive()) {
            return;
        }

        String message = String.format("📢 [리밸런싱 알림] 포트폴리오 '%d'의 자산 비중이 임계치를 초과했습니다. 현재 평가액: %,.0f KRW",
                valuation.getPortfolioId(), valuation.getTotalEvaluationBase());

        // 1. Discord Webhook 발송
        discordClient.send(setting.getDiscordWebhookUrl(), message);

        // 2. 성공 시 이력 저장
        saveHistory(setting.getPortfolio(), message, AlertType.REBALANCE_TRIGGERED, AlertStatus.SUCCESS);
        
        log.info("Successfully dispatched rebalance alert for portfolio: {}", valuation.getPortfolioId());
    }

    /**
     * 3회 재시도 모두 실패 시 실행되는 복구 로직.
     */
    @Recover
    @Transactional
    public void recover(Exception e, PortfolioValuation valuation) {
        log.error("Final failure sending alert for portfolio: {}. Reason: {}", valuation.getPortfolioId(), e.getMessage());

        AlertSetting setting = alertSettingRepository.findByPortfolioId(valuation.getPortfolioId())
                .orElseThrow();

        // 1. 알림 비활성화 (Circuit Breaking)
        setting.deactivate();

        // 2. 실패 이력 저장
        String errorMessage = "Discord 웹훅 발송 3회 실패로 인해 알림이 비활성화되었습니다.";
        saveHistory(setting.getPortfolio(), errorMessage, AlertType.ALERT_DISABLED, AlertStatus.FAILED);

        // 3. SSE 알림 발송 (사용자 실시간 인지용)
        sseEmitters.send(setting.getPortfolio().getUser().getId(), "ALERT_DISABLED", errorMessage);
    }

    private void saveHistory(Portfolio portfolio, String message, AlertType type, AlertStatus status) {
        AlertHistory history = AlertHistory.builder()
                .user(portfolio.getUser())
                .portfolio(portfolio)
                .message(message)
                .type(type)
                .status(status)
                .build();
        alertHistoryRepository.save(history);
    }
}
