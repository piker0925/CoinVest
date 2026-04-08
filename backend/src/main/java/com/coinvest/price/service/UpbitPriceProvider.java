package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.price.dto.TickerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Upbit WebSocket 기반 가격 제공자.
 */
@Slf4j
@Service
public class UpbitPriceProvider implements PriceProvider, WebSocket.Listener {

    private final String websocketUrl;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private WebSocket webSocket;
    private List<Asset> currentAssets = Collections.emptyList();
    private final Map<String, Asset> externalCodeMap = new ConcurrentHashMap<>();
    private boolean isConnecting = false;

    public UpbitPriceProvider(
            @Value("${upbit.websocket.url}") String websocketUrl,
            ObjectMapper objectMapper,
            RedisTemplate<String, Object> redisTemplate) {
        this.websocketUrl = websocketUrl;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public boolean supports(AssetClass assetClass) {
        return assetClass == AssetClass.CRYPTO;
    }

    @Override
    public List<TickerEvent> fetchPrices(List<Asset> assets) {
        return List.of();
    }

    @PostConstruct
    @Override
    public void start() {
        connect();
    }

    @PreDestroy
    @Override
    public void stop() {
        disconnect();
    }

    private void connect() {
        if (isConnected() || isConnecting) return;

        isConnecting = true;
        log.info("Connecting to Upbit WebSocket: {}", websocketUrl);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(websocketUrl), this)
                .whenComplete((ws, ex) -> {
                    isConnecting = false;
                    if (ex != null) {
                        log.error("Failed to connect to Upbit WebSocket. Retrying in 5 seconds...", ex);
                        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
                    } else {
                        this.webSocket = ws;
                        log.info("Successfully connected to Upbit WebSocket.");
                        resubscribe();
                    }
                });
    }

    private void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application shutdown");
        }
        scheduler.shutdown();
    }

    public void subscribe(List<Asset> assets) {
        this.currentAssets = assets;
        this.externalCodeMap.clear();
        assets.forEach(a -> externalCodeMap.put(a.getExternalCode(), a));
        
        if (isConnected()) {
            sendSubscriptionMessage(assets.stream().map(Asset::getExternalCode).collect(Collectors.toList()));
        }
    }

    private boolean isConnected() {
        return webSocket != null && !webSocket.isOutputClosed();
    }

    private void resubscribe() {
        if (!currentAssets.isEmpty()) {
            sendSubscriptionMessage(currentAssets.stream().map(Asset::getExternalCode).collect(Collectors.toList()));
        }
    }

    private void sendSubscriptionMessage(List<String> marketCodes) {
        if (marketCodes.isEmpty()) return;

        try {
            String ticket = UUID.randomUUID().toString();
            List<Object> subscriptionRequest = List.of(
                    Map.of("ticket", ticket),
                    Map.of("type", "ticker", "codes", marketCodes)
            );

            String json = objectMapper.writeValueAsString(subscriptionRequest);
            webSocket.sendText(json, true);
            log.info("Sent Upbit subscription request for {} markets", marketCodes.size());
        } catch (Exception e) {
            log.error("Failed to send Upbit subscription message", e);
        }
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            String message = new String(bytes, StandardCharsets.UTF_8);

            TickerEvent upbitEvent = objectMapper.readValue(message, TickerEvent.class);
            Asset asset = externalCodeMap.get(upbitEvent.getMarketCode());
            
            if (asset != null) {
                TickerEvent event = TickerEvent.builder()
                        .universalCode(asset.getUniversalCode())
                        .assetClass(asset.getAssetClass())
                        .quoteCurrency(asset.getQuoteCurrency())
                        .tradePrice(upbitEvent.getTradePrice())
                        .accTradePrice(upbitEvent.getAccTradePrice())
                        .accTradeVolume(upbitEvent.getAccTradeVolume())
                        .timestamp(upbitEvent.getTimestamp())
                        .tradeTimestamp(upbitEvent.getTradeTimestamp())
                        .build();

                // LIVE 채널 고정 발행
                redisTemplate.convertAndSend(RedisKeyConstants.getPriceTickerChannel(PriceMode.LIVE), event);
            }

        } catch (Exception e) {
            log.error("Failed to process Upbit WebSocket message", e);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("Upbit WebSocket error occurred", error);
        reconnect();
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.warn("Upbit WebSocket closed: {} (code: {})", reason, statusCode);
        reconnect();
        return null;
    }

    private void reconnect() {
        this.webSocket = null;
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }
}
