package com.coinvest.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.util.StringUtils;

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
     * 특정 사용자의 모든 Refresh Token 삭제 (탈취 감지 시)
     */
    public void deleteAllRefreshTokens(String email) {
        Set<String> keys = redisTemplate.keys(REFRESH_TOKEN_PREFIX + email + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Access Token 블랙리스트 추가
     */
    public void addToBlacklist(String accessToken, long expirationTime) {
        if (expirationTime > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + accessToken,
                    "logout",
                    expirationTime,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 블랙리스트 여부 확인
     */
    public boolean isBlacklisted(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + accessToken));
    }
}
