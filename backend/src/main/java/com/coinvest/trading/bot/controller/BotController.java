package com.coinvest.trading.bot.controller;

import com.coinvest.global.common.ApiResponse;
import com.coinvest.trading.bot.dto.BotReportResponse;
import com.coinvest.trading.bot.dto.BotSummaryResponse;
import com.coinvest.trading.bot.service.BotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 봇 조회 API.
 * 봇 데이터는 교육용 시뮬레이션 결과이므로 인증 없이 공개 조회 허용.
 */
@RestController
@RequestMapping("/api/v1/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotQueryService botQueryService;

    /** 전체 봇 목록 (전략 유형, 상태, 기간별 수익률 요약) */
    @GetMapping
    public ApiResponse<List<BotSummaryResponse>> getBots() {
        return ApiResponse.success(botQueryService.findAll());
    }

    /**
     * 봇 전략 레포트.
     * period: 1M | 3M | ALL
     * insufficient_data=true 시 아직 데이터 축적 중.
     */
    @GetMapping("/{id}/report")
    public ApiResponse<BotReportResponse> getReport(
            @PathVariable Long id,
            @RequestParam(defaultValue = "ALL") String period) {
        return ApiResponse.success(botQueryService.getReport(id, period));
    }
}
