package com.coinvest.portfolio.controller;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.ApiResponse;
import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.portfolio.dto.AlertHistoryResponse;
import com.coinvest.portfolio.dto.AlertSettingResponse;
import com.coinvest.portfolio.dto.AlertSettingUpdateRequest;
import com.coinvest.portfolio.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 알림 설정 및 이력 관리 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * 알림 설정 조회.
     */
    @GetMapping("/settings")
    public ApiResponse<AlertSettingResponse> getAlertSetting(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal User user) {
        return ApiResponse.success(alertService.getAlertSetting(portfolioId, user));
    }

    /**
     * 알림 설정 수정.
     */
    @PutMapping("/settings")
    public ApiResponse<AlertSettingResponse> updateAlertSetting(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal User user,
            @RequestBody @Valid AlertSettingUpdateRequest request) {
        return ApiResponse.success(alertService.updateAlertSetting(portfolioId, user, request));
    }

    /**
     * 알림 설정 초기화.
     */
    @DeleteMapping("/settings")
    public ApiResponse<Void> resetAlertSetting(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal User user) {
        alertService.resetAlertSetting(portfolioId, user);
        return ApiResponse.success(null);
    }

    /**
     * 알림 이력 조회.
     */
    @GetMapping("/histories")
    public ApiResponse<CursorPageResponse<AlertHistoryResponse>> getAlertHistories(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(alertService.getAlertHistories(portfolioId, user, cursorId, size));
    }
}
