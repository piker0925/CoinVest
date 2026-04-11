package com.coinvest.trading.bot.service;

import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotStatisticsJobTest {

    @InjectMocks
    private BotStatisticsJob botStatisticsJob;

    @Mock
    private TradingBotRepository botRepository;

    @Mock
    private BotStatisticsProcessor statisticsProcessor;

    @Test
    @DisplayName("모든 봇에 대해 통계 처리가 수행되어야 함")
    void execute_shouldProcessAllBots() {
        // given
        TradingBot bot1 = mock(TradingBot.class);
        TradingBot bot2 = mock(TradingBot.class);
        TradingBot bot3 = mock(TradingBot.class);
        
        when(bot1.getId()).thenReturn(1L);
        when(bot2.getId()).thenReturn(2L);
        when(bot3.getId()).thenReturn(3L);
        
        when(botRepository.findAll()).thenReturn(List.of(bot1, bot2, bot3));

        // when
        botStatisticsJob.runDailyBotStatistics();

        // then
        verify(statisticsProcessor, times(1)).processBotStatistics(bot1);
        verify(statisticsProcessor, times(1)).processBotStatistics(bot2);
        verify(statisticsProcessor, times(1)).processBotStatistics(bot3);
    }

    @Test
    @DisplayName("특정 봇 처리 실패 시에도 다른 봇 처리는 계속되어야 함")
    void execute_shouldContinueOnException() {
        // given
        TradingBot bot1 = mock(TradingBot.class);
        TradingBot bot2 = mock(TradingBot.class);
        TradingBot bot3 = mock(TradingBot.class);
        
        when(bot1.getId()).thenReturn(1L);
        when(bot2.getId()).thenReturn(2L);
        when(bot3.getId()).thenReturn(3L);

        when(botRepository.findAll()).thenReturn(List.of(bot1, bot2, bot3));
        
        doNothing().when(statisticsProcessor).processBotStatistics(bot1);
        doThrow(new RuntimeException("Error")).when(statisticsProcessor).processBotStatistics(bot2);
        doNothing().when(statisticsProcessor).processBotStatistics(bot3);

        // when
        botStatisticsJob.runDailyBotStatistics();

        // then
        verify(statisticsProcessor, times(1)).processBotStatistics(bot1);
        verify(statisticsProcessor, times(1)).processBotStatistics(bot2);
        verify(statisticsProcessor, times(1)).processBotStatistics(bot3);
    }
}
