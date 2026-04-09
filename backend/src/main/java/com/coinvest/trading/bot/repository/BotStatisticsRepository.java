package com.coinvest.trading.bot.repository;

import com.coinvest.trading.bot.domain.BotStatistics;
import com.coinvest.trading.bot.domain.TradingBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BotStatisticsRepository extends JpaRepository<BotStatistics, Long> {
    Optional<BotStatistics> findByBotAndPeriod(TradingBot bot, String period);
    
    List<BotStatistics> findByBot(TradingBot bot);
}
