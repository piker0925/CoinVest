package com.coinvest.dashboard.controller;

import com.coinvest.auth.domain.User;
import com.coinvest.dashboard.dto.BenchmarkComparison;
import com.coinvest.dashboard.dto.Period;
import com.coinvest.dashboard.dto.PerformanceResponse;
import com.coinvest.dashboard.service.BenchmarkService;
import com.coinvest.global.common.ApiResponse;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 개인화 벤치마크 대시보드 API.
 * 개인 수익률 조회 및 벤치마크 비교.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final BenchmarkService benchmarkService;

    /**
     * 내 포트폴리오 수익률 조회.
     * IDOR 방어: BenchmarkService 내에서 소유권 검증.
     *
     * @param portfolioId 조회할 포트폴리오 ID
     * @param period      조회 기간 ("1M", "3M", "ALL"), 기본값 "1M"
     */
    @GetMapping("/performance")
    public ApiResponse<PerformanceResponse> getPerformance(
            @RequestParam Long portfolioId,
            @RequestParam(defaultValue = "1M") String period,
            @AuthenticationPrincipal User user) {

        Period parsedPeriod = parsePeriod(period);
        PerformanceResponse response = benchmarkService.getMyPerformance(
            portfolioId, user.getId(), parsedPeriod
        );
        return ApiResponse.success(response);
    }

    /**
     * 벤치마크 비교 조회.
     * 내 수익률 vs 지수 vs 봇 전략.
     *
     * @param portfolioId 조회할 포트폴리오 ID
     * @param period      조회 기간 ("1M", "3M", "ALL"), 기본값 "1M"
     */
    @GetMapping("/benchmark")
    public ApiResponse<BenchmarkComparison> getBenchmarkComparison(
            @RequestParam Long portfolioId,
            @RequestParam(defaultValue = "1M") String period,
            @AuthenticationPrincipal User user) {

        Period parsedPeriod = parsePeriod(period);
        BenchmarkComparison response = benchmarkService.compareBenchmarks(
            portfolioId, user.getId(), parsedPeriod
        );
        return ApiResponse.success(response);
    }

    private Period parsePeriod(String periodCode) {
        try {
            return Period.fromCode(periodCode);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.DASHBOARD_INVALID_PERIOD);
        }
    }
}
