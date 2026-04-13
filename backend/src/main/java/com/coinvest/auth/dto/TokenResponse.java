package com.coinvest.auth.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 로그인/토큰 재발급 응답 DTO (클라이언트 노출용).
 * refreshToken은 HttpOnly 쿠키로 전달되므로 이 DTO에 포함하지 않음.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TokenResponse {
    private String accessToken;
    private String email;
    private String role;

    public static TokenResponse from(AuthTokenResult result) {
        return TokenResponse.builder()
                .accessToken(result.accessToken())
                .email(result.email())
                .role(result.role())
                .build();
    }
}
