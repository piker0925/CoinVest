package com.coinvest.dashboard.service;

import com.coinvest.dashboard.dto.BenchmarkComparison.BotReturn;
import com.coinvest.dashboard.dto.Period;
import com.coinvest.global.common.PriceMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * BotPerformanceProvider 기본 stub 구현.
 * 6A(봇 전략 엔진) 완료 전까지 빈 리스트를 반환하여
 * 대시보드 API가 봇 수익률 섹션 없이도 정상 동작하도록 보장.
 *
 * 6A에서 @Primary BotPerformanceProviderImpl을 등록하면 이 Bean은 자동 비활성화.
 */
@Service
@ConditionalOnMissingBean(value = BotPerformanceProvider.class, ignored = EmptyBotPerformanceProvider.class)
public class EmptyBotPerformanceProvider implements BotPerformanceProvider {

    @Override
    public List<BotReturn> getReturns(PriceMode mode, Period period) {
        return Collections.emptyList();
    }
}
