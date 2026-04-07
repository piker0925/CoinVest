package com.coinvest.price.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * KIS API 토큰 관리 (초슬림 & 초강력 보호 시스템).
 * 1. Redis(L1): 고속 조회 (휘발성 캐시)
 * 2. File(L2): docs/kis-token.json (영구 저장소, 도커 초기화 대응)
 * 3. Guard: 12시간 내 재발급 강제 차단 (계좌 보호)
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

    private static final String TOKEN_REDIS_KEY = "kis:access_token";
    
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile String localToken;
    private volatile Instant localExpiry;

    /**
     * 프로젝트 루트의 docs/kis-token.json 경로를 안전하게 탐색.
     */
    private Path getBackupFilePath() {
        Path path = Paths.get(System.getProperty("user.dir"));
        // backend 폴더 안에서 실행 중이라면 상위로 이동
        if (path.endsWith("backend")) {
            path = path.getParent();
        }
        return path.resolve("docs").resolve("kis-token.json");
    }

    public String getAccessToken() {
        // [Phase 1] 로컬/Redis 고속 조회
        rwLock.readLock().lock();
        try {
            if (isLocalTokenValid()) return localToken;
            String cachedToken = redisTemplate.opsForValue().get(TOKEN_REDIS_KEY);
            if (cachedToken != null) return useToken(cachedToken, 60);
        } finally {
            rwLock.readLock().unlock();
        }

        // [Phase 2] 캐시 미스 시 파일 복구 또는 신규 발급
        rwLock.writeLock().lock();
        try {
            if (isLocalTokenValid()) return localToken;

            // 1. 파일에서 복구 시도 (도커 리셋 대응)
            JsonNode fileData = loadFile();
            if (fileData != null) {
                String token = fileData.get("token").asText();
                LocalDateTime expiry = LocalDateTime.parse(fileData.get("expired_at").asText());
                
                if (expiry.isAfter(LocalDateTime.now().plusMinutes(5))) {
                    log.info("KIS Token recovered from immortal file storage (docs/kis-token.json)");
                    return storeInRedis(token, expiry);
                }
            }

            // 2. 가드 작동: 마지막 발급 이력 확인 (파일 기준)
            if (fileData != null) {
                LocalDateTime lastIssued = LocalDateTime.parse(fileData.get("issued_at").asText());
                if (LocalDateTime.now().isBefore(lastIssued.plusHours(12))) {
                    throw new IllegalStateException("🚨 [GUARD] KIS API 토큰 재발급 차단. 12시간 이내에 이미 발급받았습니다. docs/kis-token.json을 확인하세요.");
                }
            }

            // 3. 실제 API 발급
            return fetchAndPersistNewToken();

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private boolean isLocalTokenValid() {
        return localToken != null && localExpiry != null && Instant.now().isBefore(localExpiry);
    }

    private String useToken(String token, int expiresInSec) {
        this.localToken = token;
        this.localExpiry = Instant.now().plus(Duration.ofSeconds(expiresInSec - 30));
        return token;
    }

    private String storeInRedis(String token, LocalDateTime expiredAt) {
        long remainingSec = Duration.between(LocalDateTime.now(), expiredAt).toSeconds();
        redisTemplate.opsForValue().set(TOKEN_REDIS_KEY, token, Duration.ofSeconds(remainingSec));
        return useToken(token, (int) remainingSec);
    }

    private JsonNode loadFile() {
        try {
            File file = getBackupFilePath().toFile();
            if (file.exists()) return objectMapper.readTree(file);
        } catch (IOException e) {
            log.error("Failed to read token file", e);
        }
        return null;
    }

    private String fetchAndPersistNewToken() {
        log.warn("Requesting NEW KIS Access Token from API (Issuance Risk +1)");
        
        String body = String.format("{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                appKey, appSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/oauth2/tokenP"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String token = root.path("access_token").asText();
                long expiresIn = root.path("expires_in").asLong();
                LocalDateTime expiredAt = LocalDateTime.now().plusSeconds(expiresIn);

                // 1. 파일 영구 백업 (Root of Trust)
                ObjectNode node = objectMapper.createObjectNode();
                node.put("token", token);
                node.put("expired_at", expiredAt.toString());
                node.put("issued_at", LocalDateTime.now().toString());
                
                Path filePath = getBackupFilePath();
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
                log.info("KIS Token backed up to local file: {}", filePath);

                // 2. Redis 및 로컬 캐시 갱신
                return storeInRedis(token, expiredAt);
            } else {
                log.error("Failed to issue KIS token: {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to issue KIS token", e);
        }
        return null;
    }

    public String getBaseUrl() { return baseUrl; }
    public String getAppKey() { return appKey; }
    public String getAppSecret() { return appSecret; }
}
