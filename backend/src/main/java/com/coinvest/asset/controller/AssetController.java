package com.coinvest.asset.controller;

import com.coinvest.asset.dto.AssetResponse;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.global.common.ApiResponse;
import com.coinvest.global.common.PriceMode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 거래 가능 자산 목록 API.
 * - DEMO 모드: is_demo=true 자산 (가상 패러디 종목)
 * - LIVE 모드: is_demo=false 자산 (실제 종목)
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;

    @GetMapping
    public ApiResponse<List<AssetResponse>> getAssets(
            @RequestParam(defaultValue = "DEMO") PriceMode mode) {
        boolean isDemo = (mode == PriceMode.DEMO);
        List<AssetResponse> assets = assetRepository.findAllByIsDemo(isDemo)
                .stream()
                .map(AssetResponse::from)
                .toList();
        return ApiResponse.success(assets);
    }
}
