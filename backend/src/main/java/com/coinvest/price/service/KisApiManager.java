package com.coinvest.price.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * KIS API 공통 관리 (인증 토큰, ReadWriteLock 최적화).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisApiManager {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${kis.api.base-url:}")
    private String baseUrl;

    @Value("${kis.api.app-key:}")
    private String appKey;

    @Value("${kis.api.app-secret:}")
    private String appSecret;

    private static final String TOKEN_CACHE_KEY = "kis:access_token";
    
    // 로컬 캐시 및 ReadWriteLock
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile String localToken;
    private volatile Instant tokenExpiry;

    public String getAccessToken() {
        // 1. Read Lock으로 로컬 캐시 확인
        rwLock.readLock().lock();
        try {
            if (isLocalTokenValid()) {
                return localToken;
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // 2. 로컬 캐시 무효 시 Write Lock으로 진입 (갱신)
        try {
            if (rwLock.writeLock().tryLock(5, TimeUnit.SECONDS)) {
                try {
                    // Double check (다른 스레드가 이미 갱신했을 수 있음)
                    if (isLocalTokenValid()) return localToken;

                    // 3. Redis 확인
                    String cachedToken = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
                    if (cachedToken != null) {
                        this.localToken = cachedToken;
                        // Redis에 있는 경우 만료 시간은 보수적으로 1분 후로 설정 (어차피 Redis가 SSoT)
                        this.tokenExpiry = Instant.now().plus(Duration.ofMinutes(1));
                        return cachedToken;
                    }

                    // 4. Redis에도 없으면 실제 API 호출
                    return fetchNewAccessToken();
                } finally {
                    rwLock.writeLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for KIS token write lock", e);
        }
        return null;
    }

    private boolean isLocalTokenValid() {
        return localToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry);
    }

    private String fetchNewAccessToken() {
        log.info("Fetching new KIS access token from API");
        
        String body = String.format("{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                appKey, appSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/oauth2/tokenP"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String token = root.path("access_token").asText();
                long expiresIn = root.path("expires_in").asLong();
                
                // 1. Redis 저장 (SSoT)
                redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, Duration.ofSeconds(expiresIn - 60));
                
                // 2. 로컬 캐시 업데이트
                this.localToken = token;
                this.tokenExpiry = Instant.now().plus(Duration.ofSeconds(expiresIn - 120)); // 로컬은 Redis보다 조금 더 빨리 만료 유도
                
                return token;
            } else {
                log.error("Failed to fetch KIS token: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error during KIS token fetch", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null;
    }

    public String getBaseUrl() { return baseUrl; }
    public String getAppKey() { return appKey; }
    public String getAppSecret() { return appSecret; }
}
