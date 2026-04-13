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
            currentPrice = new BigDecimal("98200000");
        }

        // 현재가의 마지막 4자리를 시드로 사용: 가격이 변할 때만 호가 변화, 폴링마다 랜덤하게 깜빡이지 않음
        int seed = currentPrice.abs().remainder(new BigDecimal(10000)).intValue();

        List<OrderbookResponse.OrderbookUnit> sells = new ArrayList<>();
        List<OrderbookResponse.OrderbookUnit> buys = new ArrayList<>();

        BigDecimal tickUnit = new BigDecimal("0.001");

        for (int i = 1; i <= 5; i++) {
            BigDecimal offset = currentPrice.multiply(tickUnit.multiply(new BigDecimal(i)));

            // 호가에서 멀수록 물량이 쌓임: quantity = 0.05 + 0.10*i (deterministic)
            // seed를 이용해 레벨별로 소수점 변화를 주어 단조롭지 않게 함
            BigDecimal baseQty = new BigDecimal("0.05").add(new BigDecimal("0.10").multiply(new BigDecimal(i)));
            int noiseDigit = (seed / (int) Math.pow(10, i - 1)) % 10; // 자릿수별로 다른 값 추출
            BigDecimal noise = new BigDecimal(noiseDigit).multiply(new BigDecimal("0.01"));
            BigDecimal qty = baseQty.add(noise).setScale(4, RoundingMode.HALF_UP);

            // ratio: 잔량 비율. 멀수록 비율이 높음 (시각적 효과)
            double ratio = Math.min(100, 10.0 * i + (seed % 10));

            sells.add(0, OrderbookResponse.OrderbookUnit.builder()
                    .price(currentPrice.add(offset).setScale(0, RoundingMode.HALF_UP))
                    .quantity(qty)
                    .ratio(ratio)
                    .build());

            buys.add(OrderbookResponse.OrderbookUnit.builder()
                    .price(currentPrice.subtract(offset).setScale(0, RoundingMode.HALF_UP))
                    .quantity(qty)
                    .ratio(ratio)
                    .build());
        }

        return ApiResponse.success(OrderbookResponse.builder()
                .universalCode(universalCode)
                .sells(sells)
                .buys(buys)
                .build());
    }
}
