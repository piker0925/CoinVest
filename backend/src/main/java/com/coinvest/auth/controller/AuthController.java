package com.coinvest.auth.controller;

import com.coinvest.auth.dto.LoginRequest;
import com.coinvest.auth.dto.SignupRequest;
import com.coinvest.auth.dto.TokenResponse;
import com.coinvest.auth.dto.UserResponse;
import com.coinvest.auth.service.AuthService;
import com.coinvest.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@RequestBody @Valid SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ApiResponse.success(response);
    }

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String bearerToken) {
        String accessToken = bearerToken.substring(7);
        authService.logout(accessToken);
        return ApiResponse.success(null);
    }

    /**
     * 토큰 재발급
     */
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody String refreshToken) {
        TokenResponse response = authService.reissue(refreshToken);
        return ApiResponse.success(response);
    }
}
