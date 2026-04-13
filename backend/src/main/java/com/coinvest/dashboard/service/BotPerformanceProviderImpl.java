package com.coinvest.dashboard.service;

import com.coinvest.dashboard.dto.BenchmarkComparison.BotReturn;
import com.coinvest.dashboard.dto.Period;
import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.bot.domain.BotStatistics;
import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.repository.BotStatisticsRepository;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BotPerformanceProvider 실 구현체.
 * BotStatistics 집계 테이블에서 전략별 수익률을 조회하여 반환.
 * @Primary 선언으로 EmptyBotPerformanceProvider(stub)를 자동 대체.
 */
@Primary
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BotPerformanceProviderImpl implements BotPerformanceProvider {

    private final TradingBotRepository botRepository;
    private final BotStatisticsRepository statisticsRepository;

    @Override
    public List<BotReturn> getReturns(PriceMode mode, Period period) {
        String periodCode = period.getCode();

        return botRepository.findAll().stream()
                .filter(bot -> bot.getPriceMode() == mode)
                .flatMap(bot -> statisticsRepository.findByBotAndPeriod(bot, periodCode)
                        .filter(stat -> stat.getReturnRate() != null)
                        .map(stat -> toBotReturn(bot, stat))
                        .stream())
                .toList();
    }

    private BotReturn toBotReturn(TradingBot bot, BotStatistics stat) {
        return BotReturn.builder()
                .strategyName(bot.getStrategyType().name())
                .returnRate(stat.getReturnRate())
                .build();
    }
}
