package com.coinvest.price.service;

import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.price.dto.TickerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Upbit WebSocket 클라이언트 구현체.
 * Java HttpClient WebSocket을 사용하여 가상 스레드 환경에서 효율적으로 동작함.
 */
@Slf4j
@Service
public class UpbitWebSocketClientImpl implements UpbitWebSocketClient, WebSocket.Listener {

    private final String websocketUrl;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private WebSocket webSocket;
    private List<String> currentSubscriptions = Collections.emptyList();
    private boolean isConnecting = false;

    public UpbitWebSocketClientImpl(
            @Value("${upbit.websocket.url}") String websocketUrl,
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.websocketUrl = websocketUrl;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @PostConstruct
    @Override
    public void connect() {
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

    @PreDestroy
    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application shutdown");
        }
        scheduler.shutdown();
    }

    @Override
    public void subscribe(List<String> marketCodes) {
        this.currentSubscriptions = marketCodes;
        if (isConnected()) {
            sendSubscriptionMessage(marketCodes);
        }
    }

    @Override
    public boolean isConnected() {
        return webSocket != null && !webSocket.isOutputClosed();
    }

    private void resubscribe() {
        if (!currentSubscriptions.isEmpty()) {
            sendSubscriptionMessage(currentSubscriptions);
        }
    }

    /**
     * 업비트 프로토콜에 맞춘 구독 메시지 전송.
     * 형식: [{"ticket":"UUID"},{"type":"ticker","codes":["KRW-BTC"]}]
     */
    private void sendSubscriptionMessage(List<String> marketCodes) {
        if (marketCodes.isEmpty()) return;

        try {
            String ticket = UUID.randomUUID().toString();
            
            // 업비트 형식에 맞는 리스트 구조 생성
            List<Object> subscriptionRequest = List.of(
                    java.util.Map.of("ticket", ticket),
                    java.util.Map.of("type", "ticker", "codes", marketCodes)
            );

            String json = objectMapper.writeValueAsString(subscriptionRequest);
            webSocket.sendText(json, true);
            log.info("Sent subscription request for {} markets: {}", marketCodes.size(), marketCodes);
        } catch (Exception e) {
            log.error("Failed to send subscription message", e);
        }
    }

    /**
     * WebSocket 메시지 수신 핸들러 (바이너리).
     */
    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            String message = new String(bytes, StandardCharsets.UTF_8);

            TickerEvent event = objectMapper.readValue(message, TickerEvent.class);
            
            // Kafka로 즉시 발행
            kafkaTemplate.send(KafkaTopicConstants.PRICE_TICKER_UPDATED, event.getMarketCode(), event);

        } catch (Exception e) {
            log.error("Failed to process Upbit WebSocket message", e);
        }
        webSocket.request(1); // 다음 메시지 요청
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
        log.info("Reconnecting to Upbit WebSocket...");
        this.webSocket = null;
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }
}
