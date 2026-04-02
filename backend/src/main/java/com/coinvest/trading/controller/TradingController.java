package com.coinvest.trading.controller;

import com.coinvest.global.common.ApiResponse;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.dto.OrderPreviewRequest;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.trading.service.TradingQueryService;
import com.coinvest.trading.service.TradingService;
import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.global.common.aop.RateLimit;
import com.coinvest.trading.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;
    private final TradingQueryService tradingQueryService;
    private final UserRepository userRepository;

    @PostMapping("/orders")
    @RateLimit(key = "orders", limit = 10, window = 1)
    public ResponseEntity<ApiResponse<Long>> createOrder(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody OrderCreateRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long orderId = tradingService.createOrder(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(orderId));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @AuthenticationPrincipal String email,
            @PathVariable Long id) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        tradingService.cancelOrder(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/orders/preview")
    public ResponseEntity<ApiResponse<OrderPreviewResponse>> previewOrder(
            @Valid @RequestBody OrderPreviewRequest request) {
        OrderPreviewResponse response = tradingService.previewOrder(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<CursorPageResponse<OrderResponse>>> getOrders(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success(tradingQueryService.getOrders(user.getId(), cursor, size)));
    }

    @GetMapping("/trades")
    public ResponseEntity<ApiResponse<CursorPageResponse<TradeResponse>>> getTrades(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success(tradingQueryService.getTrades(user.getId(), cursor, size)));
    }

    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<java.util.List<PositionResponse>>> getPositions(
            @AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success(tradingQueryService.getPositions(user.getId())));
    }

    @GetMapping("/account")
    public ResponseEntity<ApiResponse<VirtualAccountResponse>> getAccount(
            @AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success(tradingQueryService.getAccount(user.getId())));
    }

    @PostMapping("/account/reset")
    public ResponseEntity<ApiResponse<Void>> resetAccount(
            @AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        tradingService.resetAccount(user.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
