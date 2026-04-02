package com.coinvest.trading;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.security.JwtTokenProvider;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.dto.OrderPreviewRequest;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import com.coinvest.trading.service.ExpiredOrderCleanupJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class TradingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ExpiredOrderCleanupJob expiredOrderCleanupJob;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("trade@example.com")
                .password("encoded_pwd")
                .nickname("trader")
                .role(UserRole.USER)
                .build();
        userRepository.save(testUser);

        VirtualAccount account = VirtualAccount.builder()
                .user(testUser)
                .balanceKrw(new BigDecimal("10000000"))
                .lockedKrw(BigDecimal.ZERO)
                .build();
        virtualAccountRepository.save(account);

        accessToken = jwtTokenProvider.createAccessToken(testUser.getEmail());

        // Redis Mocking
        String tickerKey = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, "KRW-BTC");
        redisTemplate.opsForValue().set(tickerKey, "100000000");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, "KRW-BTC"));
        redisTemplate.delete("account:reset:cooldown:" + testUser.getId());
        redisTemplate.delete("rate_limit:orders:trade@example.com");
    }

    @Test
    @DisplayName("주문 미리보기를 요청하면 계산된 예상 결과를 반환한다.")
    void previewOrder_Success() throws Exception {
        OrderPreviewRequest request = new OrderPreviewRequest(
                "KRW-BTC", OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.1"));

        mockMvc.perform(post("/api/v1/trading/orders/preview")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedPrice").value(100000000))
                .andExpect(jsonPath("$.data.expectedTotalAmount").exists());
    }

    @Test
    @DisplayName("지정가 매수 주문 생성 및 취소를 성공해야 한다.")
    void createAndCancelLimitOrder_Success() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                "KRW-BTC", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("90000000"), new BigDecimal("0.1"));

        MvcResult result = mockMvc.perform(post("/api/v1/trading/orders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(responseBody).get("data").asLong();

        mockMvc.perform(delete("/api/v1/trading/orders/" + orderId)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("시장가 매수 후 전량 매도 사이클을 검증한다.")
    void marketOrder_FullCycle_Success() throws Exception {
        OrderCreateRequest buyReq = new OrderCreateRequest(
                "KRW-BTC", OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.01"));

        mockMvc.perform(post("/api/v1/trading/orders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/trading/positions")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].quantity").value(0.01));

        OrderCreateRequest sellReq = new OrderCreateRequest(
                "KRW-BTC", OrderSide.SELL, OrderType.MARKET, null, new BigDecimal("0.01"));

        mockMvc.perform(post("/api/v1/trading/orders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/trading/positions")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(0.0));
    }

    @Test
    @DisplayName("계좌 리셋 쿨다운(24시간) 및 보상 로직을 검증한다.")
    void resetAccount_Cooldown_And_Retry_Logic() throws Exception {
        // 1. 첫 번째 리셋 성공
        mockMvc.perform(post("/api/v1/trading/account/reset")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 2. 두 번째 리셋 시도 -> 실패 (429)
        mockMvc.perform(post("/api/v1/trading/account/reset")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("주문 Rate Limit(초당 10건) 동작을 검증한다.")
    void rateLimit_Exceeded_Fails() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                "KRW-BTC", OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.001"));

        // 10번 연속 호출 (초당 제한 확인을 위해 매우 빠르게 실행)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/trading/orders")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 11번째 요청 -> 429 에러 기대
        mockMvc.perform(post("/api/v1/trading/orders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("24시간이 지난 PENDING 주문은 만료 배치에 의해 EXPIRED 처리되어야 한다.")
    void expiredOrderCleanup_Success() throws Exception {
        // 1. 지정가 주문 생성 (KRW 잠금 발생)
        OrderCreateRequest request = new OrderCreateRequest(
                "KRW-BTC", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("50000000"), new BigDecimal("0.1"));

        MvcResult result = mockMvc.perform(post("/api/v1/trading/orders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        
        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();

        // 2. 강제로 createdAt을 25시간 전으로 업데이트 (Native Query)
        entityManager.createNativeQuery("UPDATE orders SET created_at = :past WHERE id = :id")
                .setParameter("past", LocalDateTime.now().minusHours(25))
                .setParameter("id", orderId)
                .executeUpdate();
        
        entityManager.flush();
        entityManager.clear();

        // 3. 만료 Job 실행
        expiredOrderCleanupJob.cleanupExpiredOrders();

        // 4. 결과 확인: 상태 EXPIRED, 잠금 해제 확인
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        mockMvc.perform(get("/api/v1/trading/account")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockedKrw").value(0.0))
                .andExpect(jsonPath("$.data.availableBalance").value(10000000.0));
    }
}
