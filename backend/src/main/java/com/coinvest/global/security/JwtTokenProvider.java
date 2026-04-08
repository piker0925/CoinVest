package com.coinvest.global.security;

import com.coinvest.auth.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 생성 및 검증을 담당하는 유틸리티 클래스.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    private static final String ROLE_CLAIM = "role";
    private static final String ID_CLAIM = "userId";

    @PostConstruct
    protected void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access Token 생성 (ID, Role 포함)
     */
    public String createAccessToken(Long userId, String email, UserRole role) {
        return createToken(email, Map.of(ROLE_CLAIM, role.name(), ID_CLAIM, userId), accessTokenExpiration);
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(String email) {
        return createToken(email, Map.of(), refreshTokenExpiration);
    }

    private String createToken(String subject, Map<String, Object> claims, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰에서 Email(Subject) 추출
     */
    public String getEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 토큰에서 User ID 추출
     */
    public Long getUserId(String token) {
        return getClaims(token).get(ID_CLAIM, Long.class);
    }

    /**
     * 토큰에서 Role 추출
     */
    public String getRole(String token) {
        return getClaims(token).get(ROLE_CLAIM, String.class);
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰의 남은 유효 시간(ms) 계산
     */
    public long getRemainingExpirationTime(String token) {
        Date expiration = getClaims(token).getExpiration();
        long now = new Date().getTime();
        return Math.max(0, expiration.getTime() - now);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
