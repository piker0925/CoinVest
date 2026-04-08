package com.coinvest.auth.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.dto.LoginRequest;
import com.coinvest.auth.dto.SignupRequest;
import com.coinvest.auth.dto.TokenResponse;
import com.coinvest.auth.dto.UserResponse;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;
    private final LoginFailureService loginFailureService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * 회원가입
     */
    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATE);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    /**
     * 로그인
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        if (loginFailureService.isBlocked(request.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    loginFailureService.increaseAttempts(request.getEmail());
                    return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginFailureService.increaseAttempts(request.getEmail());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        loginFailureService.resetAttempts(request.getEmail());
        
        // userId 추가 전달
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        tokenService.saveRefreshToken(user.getEmail(), refreshToken, refreshTokenExpiration);

        return TokenResponse.of(accessToken, refreshToken);
    }

    /**
     * 로그아웃
     */
    @Transactional
    public void logout(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            return;
        }

        String email = jwtTokenProvider.getEmail(accessToken);
        tokenService.deleteRefreshToken(email);
        
        long remainingTime = jwtTokenProvider.getRemainingExpirationTime(accessToken);
        tokenService.addToBlacklist(accessToken, remainingTime);
    }

    /**
     * 토큰 재발급
     */
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        String email = jwtTokenProvider.getEmail(refreshToken);
        String savedRefreshToken = tokenService.getRefreshToken(email);

        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            tokenService.deleteAllRefreshTokens(email);
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // userId 추가 전달
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        tokenService.saveRefreshToken(email, newRefreshToken, refreshTokenExpiration);

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }
}
