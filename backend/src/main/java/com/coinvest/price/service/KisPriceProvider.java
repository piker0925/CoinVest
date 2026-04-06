package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.global.util.Sleeper;
import com.coinvest.portfolio.domain.AlertHistory;
import com.coinvest.portfolio.domain.AlertStatus;
import com.coinvest.portfolio.domain.AlertType;
import com.coinvest.portfolio.repository.AlertHistoryRepository;
import com.coinvest.price.dto.TickerEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisPriceProvider implements PriceProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final KisApiManager kisApiManager;
    private final AlertHistoryRepository alertHistoryRepository;
    private final Sleeper sleeper;

    private static final int MAX_RETRIES = 3;
    private static final String TR_ID_STOCK_PRICE = "FHKST01010100";

    @Override
    public boolean supports(AssetClass assetClass) {
        return assetClass == AssetClass.KR_STOCK || assetClass == AssetClass.KR_ETF;
    }

    @Override
    public List<TickerEvent> fetchPrices(List<Asset> assets) {
        if (assets.isEmpty()) return List.of();

        String token = kisApiManager.getAccessToken();
        if (token == null) {
            log.error("Failed to obtain KIS access token");
            return List.of();
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<TickerEvent>> tasks = assets.stream()
                    .map(asset -> (Callable<TickerEvent>) () -> fetchSinglePrice(asset, token))
                    .collect(Collectors.toList());

            List<TickerEvent> results = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (results.size() < assets.size()) {
                log.warn("KIS Price Fetch partial failure: {}/{} success", results.size(), assets.size());
                if (results.isEmpty()) {
                    recordFailure("KIS API 전건 실패", "Target symbols count: " + assets.size());
                }
            }

            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("KIS fetchPrices interrupted", e);
            return List.of();
        }
    }

    private TickerEvent fetchSinglePrice(Asset asset, String token) {
        // Thundering Herd 방어용 초기 Jitter (2초 분산하여 10 TPS 달성)
        try {
            long jitter = ThreadLocalRandom.current().nextLong(2000);
            sleeper.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        String uriStr = String.format("%s/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=%s",
                kisApiManager.getBaseUrl(), asset.getExternalCode());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .header("authorization", "Bearer " + token)
                .header("appkey", kisApiManager.getAppKey())
                .header("appsecret", kisApiManager.getAppSecret())
                .header("tr_id", TR_ID_STOCK_PRICE)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return fetchWithBackoff(request, asset);
    }

    private TickerEvent fetchWithBackoff(HttpRequest request, Asset asset) {
        int attempt = 0;
        long backoffDelayMs = 1000;

        while (attempt < MAX_RETRIES) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseResponse(response.body(), asset);
                } else {
                    log.warn("KIS API returned status {} for symbol {}: {}", response.statusCode(), asset.getExternalCode(), response.body());
                }
            } catch (IOException | InterruptedException e) {
                log.warn("KIS API call failed for {} (attempt {}): {}", asset.getExternalCode(), attempt + 1, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            attempt++;
            if (attempt < MAX_RETRIES) {
                applyBackoff(backoffDelayMs);
                backoffDelayMs *= 2;
            }
        }

        return null;
    }

    private TickerEvent parseResponse(String body, Asset asset) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode output = root.path("output");
            if (output.isMissingNode() || output.isNull()) return null;

            return TickerEvent.builder()
                    .universalCode(asset.getUniversalCode())
                    .assetClass(asset.getAssetClass())
                    .quoteCurrency(asset.getQuoteCurrency())
                    .tradePrice(new BigDecimal(output.path("stck_prpr").asText()))
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (IOException e) {
            log.error("Failed to parse KIS response for {}", asset.getExternalCode(), e);
        }
        return null;
    }

    private void applyBackoff(long delayMs) {
        try {
            long jitter = ThreadLocalRandom.current().nextLong(200);
            sleeper.sleep(delayMs + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordFailure(String title, String details) {
        log.error("{}: {}", title, details);
        try {
            AlertHistory alert = AlertHistory.builder()
                    .type(AlertType.SYSTEM_ERROR)
                    .status(AlertStatus.FAILED)
                    .message(title + " - " + details)
                    .build();
            alertHistoryRepository.save(alert);
        } catch (Exception e) {
            log.error("Failed to save AlertHistory for system error", e);
        }
    }
}
