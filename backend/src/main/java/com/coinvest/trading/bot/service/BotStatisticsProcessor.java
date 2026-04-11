package com.coinvest.trading.bot.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.bot.domain.BotPerformance;
import com.coinvest.trading.bot.domain.BotStatistics;
import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.repository.BotPerformanceRepository;
import com.coinvest.trading.bot.repository.BotStatisticsRepository;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.Position;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.BalanceRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotStatisticsProcessor {

    private final BotPerformanceRepository performanceRepository;
    private final BotStatisticsRepository statisticsRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final BalanceRepository balanceRepository;
    private final PositionRepository positionRepository;
    private final PriceService priceService;

    private static final List<String> PERIODS = List.of("1M", "3M", "ALL");
    private static final int MIN_DATA_POINTS = 2;

    @Transactional
    public void processBotStatistics(TradingBot bot) {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        // 1. 이미 오늘 스냅샷이 있으면 멱등성 보장
        if (performanceRepository.findByBotAndSnapshotDate(bot, today).isPresent()) {
            log.debug("BotStatistics: snapshot already exists for bot {} on {}", bot.getId(), today);
            return;
        }

        // 2. 일일 스냅샷 저장
        BigDecimal totalValue = calculateTotalAssetValue(bot);
        BigDecimal dailyReturn = calculateDailyReturn(bot, today, totalValue);
        
        BigDecimal netContribution = BigDecimal.ZERO; 

        performanceRepository.save(BotPerformance.builder()
                .bot(bot)
                .snapshotDate(today)
                .totalAssetValue(totalValue)
                .dailyReturnRate(dailyReturn)
                .netContribution(netContribution)
                .build());

        // 3. 다기간 통계 갱신
        for (String period : PERIODS) {
            updateStatistics(bot, period);
        }
    }

    private BigDecimal calculateTotalAssetValue(TradingBot bot) {
        VirtualAccount account = virtualAccountRepository.findByUserId(bot.getUserId())
                .orElse(null);
        if (account == null) return BigDecimal.ZERO;

        List<Balance> balances = balanceRepository.findAllByAccountId(account.getId());
        BigDecimal cash = balances.stream()
                .map(b -> b.getAvailable().add(b.getUnsettled()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Position> positions = positionRepository.findByUserIdAndPriceMode(
                bot.getUserId(), bot.getPriceMode());
        BigDecimal positionValue = positions.stream()
                .map(p -> {
                    BigDecimal price = priceService.getCurrentPrice(
                            p.getUniversalCode(), bot.getPriceMode());
                    return price.multiply(p.getQuantity());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return cash.add(positionValue);
    }

    private BigDecimal calculateDailyReturn(TradingBot bot, LocalDateTime today, BigDecimal todayValue) {
        LocalDateTime yesterday = today.minusDays(1);
        return performanceRepository.findByBotAndSnapshotDate(bot, yesterday)
                .map(prev -> {
                    if (prev.getTotalAssetValue().compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
                    return todayValue.subtract(prev.getTotalAssetValue())
                            .divide(prev.getTotalAssetValue(), 8, RoundingMode.HALF_UP);
                })
                .orElse(BigDecimal.ZERO);
    }

    private void updateStatistics(TradingBot bot, String period) {
        List<BotPerformance> history = getHistory(bot, period);
        if (history.size() < MIN_DATA_POINTS) {
            upsertStatistics(bot, period, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
            return;
        }

        BigDecimal returnRate = calculateReturnRate(history);
        BigDecimal mdd = calculateMdd(history);
        BigDecimal winRate = calculateWinRate(history);
        BigDecimal sharpeRatio = calculateSharpeRatio(history);
        int tradeCount = history.size();

        upsertStatistics(bot, period, returnRate, mdd, sharpeRatio, winRate, tradeCount);
    }

    private BigDecimal calculateSharpeRatio(List<BotPerformance> history) {
        if (history.size() < MIN_DATA_POINTS) return BigDecimal.ZERO;

        BigDecimal sumReturns = BigDecimal.ZERO;
        for (BotPerformance p : history) {
            sumReturns = sumReturns.add(p.getDailyReturnRate());
        }
        BigDecimal mean = sumReturns.divide(BigDecimal.valueOf(history.size()), 8, RoundingMode.HALF_UP);

        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (BotPerformance p : history) {
            BigDecimal diff = p.getDailyReturnRate().subtract(mean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(history.size()), 8, RoundingMode.HALF_UP);

        double stdDevDouble = Math.sqrt(variance.doubleValue());
        if (stdDevDouble == 0.0) return BigDecimal.ZERO;

        BigDecimal stdDev = BigDecimal.valueOf(stdDevDouble);
        return mean.divide(stdDev, 4, RoundingMode.HALF_UP);
    }

    private List<BotPerformance> getHistory(TradingBot bot, String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case "1M" -> performanceRepository.findByBotAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                    bot, now.minusMonths(1));
            case "3M" -> performanceRepository.findByBotAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                    bot, now.minusMonths(3));
            default -> performanceRepository.findByBotOrderBySnapshotDateAsc(bot);
        };
    }

    private BigDecimal calculateReturnRate(List<BotPerformance> history) {
        BigDecimal first = history.get(0).getTotalAssetValue();
        BigDecimal last = history.get(history.size() - 1).getTotalAssetValue();
        if (first.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return last.subtract(first).divide(first, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMdd(List<BotPerformance> history) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal mdd = BigDecimal.ZERO;

        for (BotPerformance p : history) {
            BigDecimal value = p.getTotalAssetValue();
            if (value.compareTo(peak) > 0) peak = value;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = value.subtract(peak).divide(peak, 8, RoundingMode.HALF_UP);
                if (drawdown.compareTo(mdd) < 0) mdd = drawdown;
            }
        }
        return mdd;
    }

    private BigDecimal calculateWinRate(List<BotPerformance> history) {
        long winDays = history.stream()
                .filter(p -> p.getDailyReturnRate().compareTo(BigDecimal.ZERO) > 0)
                .count();
        return BigDecimal.valueOf(winDays)
                .divide(BigDecimal.valueOf(history.size()), 8, RoundingMode.HALF_UP);
    }

    private void upsertStatistics(TradingBot bot, String period,
                                  BigDecimal returnRate, BigDecimal mdd, BigDecimal sharpeRatio,
                                  BigDecimal winRate, int tradeCount) {
        statisticsRepository.findByBotAndPeriod(bot, period)
                .ifPresentOrElse(
                        stat -> stat.update(returnRate, mdd, sharpeRatio, winRate, tradeCount),
                        () -> statisticsRepository.save(BotStatistics.builder()
                                .bot(bot).period(period)
                                .returnRate(returnRate).mdd(mdd)
                                .sharpeRatio(sharpeRatio).winRate(winRate)
                                .tradeCount(tradeCount)
                                .build())
                );
    }
}
