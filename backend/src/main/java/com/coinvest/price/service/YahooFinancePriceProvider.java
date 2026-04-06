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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinancePriceProvider implements PriceProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AlertHistoryRepository alertHistoryRepository;
    private final Sleeper sleeper;

    private static final int MAX_RETRIES = 3;
    private static final int CHUNK_SIZE = 20;

    @Value("${yahoo.api.base-url:https://query1.finance.yahoo.com/v7/finance/quote}")
    private String baseUrl;

    @Override
    public boolean supports(AssetClass assetClass) {
        return assetClass == AssetClass.US_STOCK || assetClass == AssetClass.US_ETF;
    }

    @Override
    public List<TickerEvent> fetchPrices(List<Asset> assets) {
        if (assets.isEmpty()) return List.of();

        List<List<Asset>> chunks = new ArrayList<>();
        for (int i = 0; i < assets.size(); i += CHUNK_SIZE) {
            chunks.add(assets.subList(i, Math.min(i + CHUNK_SIZE, assets.size())));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<List<TickerEvent>>> tasks = chunks.stream()
                    .map(chunk -> (Callable<List<TickerEvent>>) () -> fetchChunk(chunk))
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
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

            if (results.size() < assets.size()) {
                log.warn("Yahoo Finance Fetch partial failure: {}/{} success", results.size(), assets.size());
            }

            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Yahoo Finance fetchPrices interrupted", e);
            return List.of();
        }
    }

    private List<TickerEvent> fetchChunk(List<Asset> chunk) {
        try {
            long jitter = ThreadLocalRandom.current().nextLong(1000);
            sleeper.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }

        String symbols = chunk.stream()
                .map(Asset::getExternalCode)
                .collect(Collectors.joining(","));

        URI uri = URI.create(baseUrl + "?symbols=" + symbols);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return fetchWithBackoff(request, chunk);
    }

    private List<TickerEvent> fetchWithBackoff(HttpRequest request, List<Asset> chunk) {
        int attempt = 0;
        long backoffDelayMs = 1000;

        while (attempt < MAX_RETRIES) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseResponse(response.body(), chunk);
                } else {
                    log.warn("Yahoo Finance API returned status {}: {}", response.statusCode(), response.body());
                }
            } catch (IOException | InterruptedException e) {
                log.warn("Yahoo Finance API call failed (attempt {}): {}", attempt + 1, e.getMessage());
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

        recordFailure("Yahoo Finance API 최종 실패", "Symbols: " + chunk.stream().map(Asset::getExternalCode).collect(Collectors.joining(",")));
        return List.of();
    }

    private List<TickerEvent> parseResponse(String body, List<Asset> assets) {
        List<TickerEvent> events = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("quoteResponse").path("result");

            Map<String, Asset> assetMap = assets.stream()
                    .collect(Collectors.toMap(Asset::getExternalCode, a -> a));

            for (JsonNode node : results) {
                String symbol = node.path("symbol").asText();
                Asset asset = assetMap.get(symbol);
                if (asset != null && node.has("regularMarketPrice")) {
                    events.add(TickerEvent.builder()
                            .universalCode(asset.getUniversalCode())
                            .assetClass(asset.getAssetClass())
                            .quoteCurrency(asset.getQuoteCurrency())
                            .tradePrice(new BigDecimal(node.path("regularMarketPrice").asText()))
                            .timestamp(System.currentTimeMillis())
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse Yahoo Finance response", e);
        }
        return events;
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
            log.error("Failed to save AlertHistory for Yahoo Finance system error", e);
        }
    }
}
