package com.coinvest.trading.bot.service;

import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 봇 성과 통계 및 스냅샷 생성을 위한 스케줄링 잡.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotStatisticsJob {

    private final TradingBotRepository botRepository;
    private final BotStatisticsProcessor statisticsProcessor;

    /**
     * 매일 자정(KST) 봇 일일 성과 스냅샷 저장 및 통계 갱신.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runDailyBotStatistics() {
        log.info("Starting daily bot statistics job...");
        List<TradingBot> bots = botRepository.findAll();
        
        for (TradingBot bot : bots) {
            try {
                // 메서드명 processBotStatistics로 통일
                statisticsProcessor.processBotStatistics(bot);
            } catch (Exception e) {
                log.error("Failed to process statistics for botId={}: {}", bot.getId(), e.getMessage());
            }
        }
        log.info("Daily bot statistics job completed.");
    }
}
