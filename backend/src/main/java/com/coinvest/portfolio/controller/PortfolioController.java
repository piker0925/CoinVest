package com.coinvest.portfolio.controller;

import com.coinvest.global.common.ApiResponse;
import com.coinvest.portfolio.dto.PortfolioCreateRequest;
import com.coinvest.portfolio.dto.PortfolioResponse;
import com.coinvest.portfolio.dto.RebalancingProposal;
import com.coinvest.portfolio.service.PortfolioService;
import com.coinvest.portfolio.service.RebalancingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 포트폴리오 관리 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final RebalancingService rebalancingService;

    /**
     * 포트폴리오 생성.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PortfolioResponse> createPortfolio(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid PortfolioCreateRequest request) {
        return ApiResponse.success(portfolioService.createPortfolio(userId, request));
    }

    /**
     * 내 포트폴리오 목록 조회.
     */
    @GetMapping
    public ApiResponse<List<PortfolioResponse>> getPortfolios(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(portfolioService.getPortfolios(userId));
    }

    /**
     * 포트폴리오 상세 조회.
     */
    @GetMapping("/{id}")
    public ApiResponse<PortfolioResponse> getPortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(portfolioService.getPortfolio(id, userId));
    }

    /**
     * 리밸런싱 시뮬레이션 제안 조회.
     */
    @GetMapping("/{id}/rebalancing")
    public ApiResponse<List<RebalancingProposal>> simulateRebalancing(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(rebalancingService.simulateRebalancing(id, userId));
    }

    /**
     * 포트폴리오 수정.
     */
    @PutMapping("/{id}")
    public ApiResponse<PortfolioResponse> updatePortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid com.coinvest.portfolio.dto.PortfolioUpdateRequest request) {
        return ApiResponse.success(portfolioService.updatePortfolio(id, userId, request));
    }

    /**
     * 포트폴리오 삭제.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deletePortfolio(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        portfolioService.deletePortfolio(id, userId);
        return ApiResponse.success(null);
    }
}
