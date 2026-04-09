package com.coinvest.trading.bot.repository;

import com.coinvest.trading.bot.domain.BotPerformance;
import com.coinvest.trading.bot.domain.TradingBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BotPerformanceRepository extends JpaRepository<BotPerformance, Long> {
    Optional<BotPerformance> findByBotAndSnapshotDate(TradingBot bot, LocalDate snapshotDate);
    
    List<BotPerformance> findByBotAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(TradingBot bot, LocalDate snapshotDate);
    
    List<BotPerformance> findByBotOrderBySnapshotDateAsc(TradingBot bot);
}
