package com.coinvest.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 토큰 영속성 관리 서비스 (Redis 활용).
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    /**
     * Refresh Token 저장 (Rotation 고려하여 Email 외에 고유 식별자 추가 권장되나, 현재는 간소화하여 Email 기반 세션 관리)
     * Theft Detection을 위해 Email:Token 형식을 키로 사용하거나, Set으로 관리 가능.
     * 여기서는 Email별로 현재 유효한 RT를 저장.
     */
    public void saveRefreshToken(String email, String refreshToken, long expirationTime) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + email,
                refreshToken,
                expirationTime,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Refresh Token 조회
     */
    public String getRefreshToken(String email) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + email);
    }

    /**
     * Refresh Token 삭제 (로그아웃 시)
     */
    public void deleteRefreshToken(String email) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + email);
    }

    /**
     * 특정 사용자의 모든 Refresh Token 삭제 (탈취 감지 시).
     * KEYS 대신 SCAN 커서를 사용하여 Redis 블로킹을 방지함.
     */
    public void deleteAllRefreshTokens(String email) {
        String pattern = REFRESH_TOKEN_PREFIX + email + "*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        List<String> keys = new ArrayList<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }

        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Access Token 블랙리스트 추가.
     * 토큰 원문을 Redis 키로 사용하면 DEBUG 로그에 노출될 수 있으므로 MD5 해시를 키로 사용함.
     */
    public void addToBlacklist(String accessToken, long expirationTime) {
        if (expirationTime > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + hashToken(accessToken),
                    "logout",
                    expirationTime,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 블랙리스트 여부 확인.
     * addToBlacklist()와 동일한 해시 함수 사용.
     */
    public boolean isBlacklisted(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + hashToken(accessToken)));
    }

    /**
     * 토큰 원문 노출 방지를 위해 Redis 키 생성 시 MD5 해시 적용.
     * 보안 키가 아닌 식별자 목적이므로 MD5 충분.
     */
    private String hashToken(String token) {
        return DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
    }
}
