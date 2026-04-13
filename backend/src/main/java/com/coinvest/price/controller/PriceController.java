package com.coinvest.price.controller;

import com.coinvest.global.common.ApiResponse;
import com.coinvest.global.common.PriceMode;
import com.coinvest.price.dto.CandleData;
import com.coinvest.price.dto.OrderbookResponse;
import com.coinvest.price.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/ticker")
    public ApiResponse<BigDecimal> getTicker(
            @RequestParam String universalCode,
            @RequestParam(defaultValue = "LIVE") PriceMode mode) {
        return ApiResponse.success(priceService.getCurrentPrice(universalCode, mode));
    }

    @GetMapping("/candles")
    public ApiResponse<List<CandleData>> getCandles(
            @RequestParam String universalCode,
            @RequestParam(defaultValue = "LIVE") PriceMode mode) {
        return ApiResponse.success(priceService.getCandles(universalCode, mode));
    }

    @GetMapping("/orderbook")
    public ApiResponse<OrderbookResponse> getOrderbook(
            @RequestParam String universalCode,
            @RequestParam(defaultValue = "LIVE") PriceMode mode) {
        
        BigDecimal currentPrice = priceService.getCurrentPrice(universalCode, mode);
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            currentPrice = new BigDecimal("98200000"); // Fallback for demo
        }

        // 진짜 호가창 API 연동 전까지는 현재가 기준 시뮬레이션 데이터 반환
        List<OrderbookResponse.OrderbookUnit> sells = new ArrayList<>();
        List<OrderbookResponse.OrderbookUnit> buys = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            BigDecimal offset = currentPrice.multiply(new BigDecimal("0.001").multiply(new BigDecimal(i)));
            sells.add(0, OrderbookResponse.OrderbookUnit.builder()
                    .price(currentPrice.add(offset).setScale(0, RoundingMode.HALF_UP))
                    .quantity(new BigDecimal(Math.random() * 0.5).setScale(4, RoundingMode.HALF_UP))
                    .ratio(Math.random() * 100)
                    .build());
            
            buys.add(OrderbookResponse.OrderbookUnit.builder()
                    .price(currentPrice.subtract(offset).setScale(0, RoundingMode.HALF_UP))
                    .quantity(new BigDecimal(Math.random() * 0.5).setScale(4, RoundingMode.HALF_UP))
                    .ratio(Math.random() * 100)
                    .build());
        }

        return ApiResponse.success(OrderbookResponse.builder()
                .universalCode(universalCode)
                .sells(sells)
                .buys(buys)
                .build());
    }
}
