package com.coinvest.auth.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.dto.LoginRequest;
import com.coinvest.auth.dto.SignupRequest;
import com.coinvest.auth.dto.TokenResponse;
import com.coinvest.auth.dto.UserResponse;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
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
     * 로그인 (Brute-force 방어 포함)
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        // 1. 차단 여부 확인
        if (loginFailureService.isBlocked(request.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    loginFailureService.increaseAttempts(request.getEmail());
                    return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        // 2. 계정 활성화 여부 확인 (Soft-delete 대응)
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginFailureService.increaseAttempts(request.getEmail());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // 4. 성공 시 실패 기록 초기화 및 토큰 발급
        loginFailureService.resetAttempts(request.getEmail());
        
        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        tokenService.saveRefreshToken(user.getEmail(), refreshToken, refreshTokenExpiration);

        return TokenResponse.of(accessToken, refreshToken);
    }

    /**
     * 로그아웃 (Stateless)
     */
    @Transactional
    public void logout(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            return;
        }

        String email = jwtTokenProvider.getEmail(accessToken);
        tokenService.deleteRefreshToken(email);
        
        // Access Token 블랙리스트 추가 (남은 유효 시간만큼)
        long remainingTime = jwtTokenProvider.getRemainingExpirationTime(accessToken);
        tokenService.addToBlacklist(accessToken, remainingTime);
    }

    /**
     * 토큰 재발급 (RTR + Theft Detection)
     */
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        // 1. 토큰 자체 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        String email = jwtTokenProvider.getEmail(refreshToken);
        String savedRefreshToken = tokenService.getRefreshToken(email);

        // 2. Theft Detection (이미 사용된 토큰이거나 존재하지 않는 경우)
        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            // 탈취로 간주하고 해당 사용자의 모든 세션 무효화
            tokenService.deleteAllRefreshTokens(email);
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 3. RTR 적용: 새 토큰 발급 및 기존 토큰 무효화
        String newAccessToken = jwtTokenProvider.createAccessToken(email);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(email);

        tokenService.saveRefreshToken(email, newRefreshToken, refreshTokenExpiration);

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }
}
