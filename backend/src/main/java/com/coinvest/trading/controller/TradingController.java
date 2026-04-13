package com.coinvest.trading.controller;

import com.coinvest.global.common.ApiResponse;
import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.trading.dto.*;
import com.coinvest.trading.service.TradingQueryService;
import com.coinvest.trading.service.TradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;
    private final TradingQueryService tradingQueryService;

    @PostMapping("/orders")
    public ApiResponse<Long> createOrder(
            Authentication authentication,
            @Valid @RequestBody OrderCreateRequest request) {
        Long userId = getUserId(authentication);
        PriceMode mode = resolvePriceMode(authentication);
        return ApiResponse.success(tradingService.createOrder(userId, request, mode));
    }

    @PostMapping("/orders/preview")
    public ApiResponse<OrderPreviewResponse> previewOrder(
            Authentication authentication,
            @Valid @RequestBody OrderPreviewRequest request) {
        PriceMode mode = resolvePriceMode(authentication);
        return ApiResponse.success(tradingService.previewOrder(request, mode));
    }

    @GetMapping("/account")
    public ApiResponse<VirtualAccountResponse> getAccount(Authentication authentication) {
        return ApiResponse.success(tradingQueryService.getAccount(getUserId(authentication)));
    }

    @GetMapping("/positions")
    public ApiResponse<List<PositionResponse>> getPositions(Authentication authentication) {
        return ApiResponse.success(tradingQueryService.getPositions(getUserId(authentication)));
    }

    @GetMapping("/orders")
    public ApiResponse<CursorPageResponse<OrderResponse>> getOrders(
            Authentication authentication,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tradingQueryService.getOrders(getUserId(authentication), cursorId, size));
    }

    @GetMapping("/trades")
    public ApiResponse<CursorPageResponse<TradeResponse>> getTrades(
            Authentication authentication,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tradingQueryService.getTrades(getUserId(authentication), cursorId, size));
    }

    @DeleteMapping("/orders/{orderId}")
    public ApiResponse<Void> cancelOrder(
            Authentication authentication,
            @PathVariable Long orderId) {
        tradingService.cancelOrder(getUserId(authentication), orderId);
        return ApiResponse.success(null);
    }

    @PostMapping("/account/reset")
    public ApiResponse<Void> resetAccount(Authentication authentication) {
        tradingService.resetAccount(getUserId(authentication));
        return ApiResponse.success(null);
    }

    private Long getUserId(Authentication authentication) {
        // 토큰 기반 인증이므로 Principal이 email일 것임. 
        // 실제 운영 환경에선 CustomUserDetails를 통해 ID를 바로 가져오도록 개선 가능.
        // 현재는 편의상 별도 조회 없이 이메일을 User 식별자로 사용하거나 Repository에서 조회 필요.
        // (Auth 연동 시점에 맞춰 최적화 예정)
        return (Long) authentication.getPrincipal(); // Filter에서 ID를 넣도록 되어있는지 확인 필요
    }

    private PriceMode resolvePriceMode(Authentication authentication) {
        if (authentication == null) return PriceMode.DEMO;
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return PriceModeResolver.resolveFromAuthorities(authorities);
    }
}
