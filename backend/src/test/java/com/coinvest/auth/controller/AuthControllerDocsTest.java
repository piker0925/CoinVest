package com.coinvest.auth.controller;

import com.coinvest.auth.dto.AuthTokenResult;
import com.coinvest.auth.dto.LoginRequest;
import com.coinvest.auth.dto.TokenResponse;
import com.coinvest.auth.service.AuthService;
import com.coinvest.global.restdocs.RestDocsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import(AuthControllerDocsTest.RestTemplateBuilderConfig.class)
class AuthControllerDocsTest extends RestDocsSupport {

    @org.springframework.boot.test.context.TestConfiguration
    static class RestTemplateBuilderConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder() {
            return new org.springframework.boot.web.client.RestTemplateBuilder();
        }
    }

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.coinvest.auth.service.TokenService tokenService;

    @MockitoBean
    private com.coinvest.global.security.JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // Removed RestTemplateBuilder mocking
    }

    @Test
    @DisplayName("로그인 API 문서화")
    void login() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        AuthTokenResult response = new AuthTokenResult("access-token", "refresh-token", "test@example.com", "ROLE_USER");

        given(authService.login(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(document("auth-login",
                        requestFields(
                                fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                                fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지").optional(),
                                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                                fieldWithPath("data.email").type(JsonFieldType.STRING).description("사용자 이메일"),
                                fieldWithPath("data.role").type(JsonFieldType.STRING).description("사용자 역할")
                        )
                ));
    }
}
