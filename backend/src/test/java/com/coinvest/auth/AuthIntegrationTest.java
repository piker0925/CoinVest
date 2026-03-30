package com.coinvest.auth;

import com.coinvest.auth.dto.LoginRequest;
import com.coinvest.auth.dto.SignupRequest;
import com.coinvest.auth.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local") // local 프로파일에서 Redis/DB 연동 필요 (실제 환경에서는 Testcontainers 추천)
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("회원가입 후 로그인이 정상적으로 수행되어야 한다.")
    void signupAndLoginSuccess() throws Exception {
        // 1. 회원가입
        SignupRequest signupRequest = new SignupRequest("test@example.com", "password123!", "tester");
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // 2. 로그인
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123!");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @DisplayName("비밀번호 5회 실패 시 계정이 차단되어야 한다 (429 Too Many Requests).")
    void bruteForceProtection() throws Exception {
        // 1. 회원가입
        SignupRequest signupRequest = new SignupRequest("brute@example.com", "password123!", "brute");
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        // 2. 5회 로그인 실패 시도
        LoginRequest wrongRequest = new LoginRequest("brute@example.com", "wrong_password");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(wrongRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 3. 6회째 시도 시 429 에러 반환 확인
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("로그아웃된 Access Token으로 요청 시 401 에러를 반환해야 한다.")
    void logoutAndTokenInvalidation() throws Exception {
        // 1. 회원가입 및 로그인
        SignupRequest signupRequest = new SignupRequest("logout@example.com", "password123!", "logout");
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("logout@example.com", "password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseBody).get("data").get("accessToken").asText();

        // 2. 로그아웃
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 3. 로그아웃된 토큰으로 재요청 (현재는 /reissue나 다른 인증 필요한 API가 없으므로 Security 필터 작동 확인)
        // SecurityConfig에서 인증이 필요한 아무 API(예: /api/v1/auth/reissue는 permitAll이므로 제외) 시도
        // 여기선 로그아웃 API 자체에 다시 요청하여 블랙리스트 작동 확인
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("이미 사용된 Refresh Token으로 재발급 시도 시 Theft Detection이 작동해야 한다.")
    void refreshTokenTheftDetection() throws Exception {
        // 1. 회원가입 및 로그인
        SignupRequest signupRequest = new SignupRequest("theft@example.com", "password123!", "theft");
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("theft@example.com", "password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();

        // 2. 1차 재발급 (정상)
        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.TEXT_PLAIN)
                .content(refreshToken))
                .andExpect(status().isOk());

        // 3. 2차 재발급 시도 (이미 사용된 RT - Theft Detection)
        mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.TEXT_PLAIN)
                .content(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }
}
