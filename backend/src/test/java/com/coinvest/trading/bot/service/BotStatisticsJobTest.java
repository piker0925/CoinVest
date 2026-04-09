package com.coinvest.trading.bot.service;

import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotStatisticsJobTest {

    @InjectMocks
    private BotStatisticsJob botStatisticsJob;

    @Mock
    private TradingBotRepository botRepository;

    @Mock
    private BotStatisticsProcessor statisticsProcessor;

    @Test
    @DisplayName("봇 통계 배치 처리 중 1개 봇에서 예외가 발생하더라도 다음 봇은 정상적으로 처리되어야 한다 (결함 전파 차단)")
    void should_continue_processing_when_one_bot_fails() {
        // given
        TradingBot bot1 = TradingBot.builder().build();
        ReflectionTestUtils.setField(bot1, "id", 1L);

        TradingBot bot2 = TradingBot.builder().build();
        ReflectionTestUtils.setField(bot2, "id", 2L);

        TradingBot bot3 = TradingBot.builder().build();
        ReflectionTestUtils.setField(bot3, "id", 3L);

        List<TradingBot> bots = Arrays.asList(bot1, bot2, bot3);

        given(botRepository.findAll()).willReturn(bots);

        // 2번 봇 처리 시 예외 발생
        doNothing().when(statisticsProcessor).process(bot1);
        doThrow(new RuntimeException("Processing Error for Bot 2")).when(statisticsProcessor).process(bot2);
        doNothing().when(statisticsProcessor).process(bot3);

        // when
        botStatisticsJob.execute();

        // then
        // 2번이 실패했음에도 불구하고 1, 2, 3 모두 process가 호출되었는지 검증
        verify(statisticsProcessor, times(1)).process(bot1);
        verify(statisticsProcessor, times(1)).process(bot2);
        verify(statisticsProcessor, times(1)).process(bot3);
    }
}
