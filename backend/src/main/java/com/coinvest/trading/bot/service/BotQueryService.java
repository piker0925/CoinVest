package com.coinvest.trading.bot.service;

import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.trading.bot.domain.BotStatistics;
import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.dto.BotReportResponse;
import com.coinvest.trading.bot.dto.BotSummaryResponse;
import com.coinvest.trading.bot.repository.BotStatisticsRepository;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BotQueryService {

    private final TradingBotRepository botRepository;
    private final BotStatisticsRepository statisticsRepository;

    public List<BotSummaryResponse> findAll() {
        return botRepository.findAll().stream()
                .map(bot -> {
                    Map<String, BotStatistics> statsMap = statisticsRepository.findByBot(bot)
                            .stream().collect(Collectors.toMap(BotStatistics::getPeriod, s -> s));
                    return BotSummaryResponse.of(
                            bot,
                            statsMap.get("1M"),
                            statsMap.get("3M"),
                            statsMap.get("ALL")
                    );
                })
                .toList();
    }

    public BotReportResponse getReport(Long botId, String period) {
        TradingBot bot = botRepository.findById(botId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ASSET_NOT_FOUND));
        BotStatistics stat = statisticsRepository.findByBotAndPeriod(bot, period)
                .orElse(null);
        return BotReportResponse.from(botId, period, stat);
    }
}
