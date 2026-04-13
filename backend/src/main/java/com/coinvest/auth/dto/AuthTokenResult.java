package com.coinvest.auth.dto;

/**
 * 인증 서비스 레이어 내부 전달 객체.
 * accessToken은 응답 Body, refreshToken은 HttpOnly 쿠키로 분리 처리됨.
 */
public record AuthTokenResult(
        String accessToken,
        String refreshToken,
        String email,
        String role   // "ROLE_USER", "ROLE_ADMIN"
) {}
