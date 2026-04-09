package com.coinvest.trading.bot.repository;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.bot.domain.BotStatus;
import com.coinvest.trading.bot.domain.TradingBot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TradingBotRepository extends JpaRepository<TradingBot, Long> {
    Optional<TradingBot> findByUserId(Long userId);
    
    List<TradingBot> findByStatus(BotStatus status);
    
    List<TradingBot> findByStatusAndPriceMode(BotStatus status, PriceMode priceMode);

    @Query("SELECT b FROM TradingBot b WHERE b.status = :status")
    List<TradingBot> findActiveBots(BotStatus status);
}
