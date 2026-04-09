package com.coinvest.trading.bot.service;

import com.coinvest.trading.bot.domain.BotStatus;
import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 봇 성과 및 통계 계산을 위한 경량 배치 작업.
 * 매일 자정에 실행됨.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotStatisticsJob {

    private final TradingBotRepository botRepository;
    private final BotStatisticsProcessor statisticsProcessor;

    @Scheduled(cron = "0 0 0 * * *")
    public void execute() {
        log.info("Starting BotStatisticsJob...");
        
        // ACTIVE 상태인 봇들만 대상 (또는 PAUSED 포함)
        List<TradingBot> bots = botRepository.findAll(); 

        for (TradingBot bot : bots) {
            try {
                statisticsProcessor.process(bot);
            } catch (Exception e) {
                log.error("Failed to process statistics for bot {}: {}", bot.getId(), e.getMessage());
            }
        }
        
        log.info("BotStatisticsJob completed.");
    }
}
