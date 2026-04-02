package com.coinvest.global.common.aop;

import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Redis Lua Script를 활용한 논블로킹 기반의 분산 처리 Rate Limiter AOP.
 * Virtual Thread 환경에서 Thread Pinning을 방지하기 위해 synchronized 없이 단일 스크립트로 원자적 처리.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    // KEYS[1]: rate limit key, ARGV[1]: limit value, ARGV[2]: expiry time (seconds)
    private static final String LUA_SCRIPT = 
            "local current = redis.call('get', KEYS[1]) " +
            "if current and tonumber(current) >= tonumber(ARGV[1]) then " +
            "return 0 " +
            "end " +
            "current = redis.call('incr', KEYS[1]) " +
            "if tonumber(current) == 1 then " +
            "redis.call('expire', KEYS[1], tonumber(ARGV[2])) " +
            "end " +
            "return 1 ";

    private final RedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String identifier = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
        
        String key = "rate_limit:" + rateLimit.key() + ":" + identifier;
        
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(rateLimit.limit()),
                String.valueOf(rateLimit.window())
        );

        if (result == null || result == 0L) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new BusinessException(ErrorCode.COMMON_TOO_MANY_REQUESTS);
        }

        return joinPoint.proceed();
    }
}
