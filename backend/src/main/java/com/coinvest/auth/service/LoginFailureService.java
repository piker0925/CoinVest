package com.coinvest.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 로그인 실패 횟수 관리 및 차단 로직 (Brute-force 방어).
 */
@Service
@RequiredArgsConstructor
public class LoginFailureService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOGIN_ATTEMPTS_PREFIX = "auth:login-attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MS = 30 * 60 * 1000; // 30분

    /**
     * 로그인 실패 횟수 증가 및 TTL 설정
     */
    public void increaseAttempts(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        Long attempts = redisTemplate.opsForValue().increment(key);
        
        // 첫 실패 시 또는 TTL이 없는 경우 30분 TTL 강제 설정 (OOM 방지)
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOCK_TIME_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 로그인 차단 여부 확인
     */
    public boolean isBlocked(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        String attempts = redisTemplate.opsForValue().get(key);
        return attempts != null && Integer.parseInt(attempts) >= MAX_ATTEMPTS;
    }

    /**
     * 로그인 성공 시 실패 기록 초기화
     */
    public void resetAttempts(String email) {
        redisTemplate.delete(LOGIN_ATTEMPTS_PREFIX + email);
    }
}
