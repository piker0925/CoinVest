package com.coinvest.trading.bot.repository;

import com.coinvest.trading.bot.domain.BotPerformance;
import com.coinvest.trading.bot.domain.TradingBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BotPerformanceRepository extends JpaRepository<BotPerformance, Long> {
    Optional<BotPerformance> findByBotAndSnapshotDate(TradingBot bot, LocalDateTime snapshotDate);
    List<BotPerformance> findByBotAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(TradingBot bot, LocalDateTime snapshotDate);
    List<BotPerformance> findByBotOrderBySnapshotDateAsc(TradingBot bot);
}
