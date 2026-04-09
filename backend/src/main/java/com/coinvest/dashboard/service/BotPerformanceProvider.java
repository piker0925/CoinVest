package com.coinvest.dashboard.service;

import com.coinvest.dashboard.dto.BenchmarkComparison.BotReturn;
import com.coinvest.dashboard.dto.Period;
import com.coinvest.global.common.PriceMode;

import java.util.List;

/**
 * 봇 전략별 수익률 제공 인터페이스.
 * 6A(봇 전략 엔진) 구현 전까지는 EmptyBotPerformanceProvider(stub)가 사용되며,
 * 6A 완료 시 @Primary 실 구현체가 자동 대체.
 */
public interface BotPerformanceProvider {

    /**
     * 지정 기간의 봇 전략별 수익률 목록 반환.
     *
     * @param mode   DEMO / LIVE 모드
     * @param period 조회 기간
     * @return 봇 전략별 수익률 목록 (6A 미구현 시 빈 리스트)
     */
    List<BotReturn> getReturns(PriceMode mode, Period period);
}
