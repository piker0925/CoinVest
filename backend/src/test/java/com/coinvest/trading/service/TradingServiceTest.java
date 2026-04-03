package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.TradeRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingServiceTest {

    @InjectMocks
    private TradingService tradingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private org.springframework.data.redis.core.ZSetOperations<String, Object> zSetOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private User testUser;
    private VirtualAccount virtualAccount;
    private final Long userId = 1L;
    private final String marketCode = "KRW-BTC";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encoded_password")
                .nickname("Tester")
                .build();
        ReflectionTestUtils.setField(testUser, "id", userId);

        virtualAccount = VirtualAccount.builder()
                .user(testUser)
                .balanceKrw(new BigDecimal("10000000"))
                .lockedKrw(BigDecimal.ZERO)
                .build();
        
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
    }

    @Test
    @DisplayName("시장가 매수 정상 처리 - 기존 포지션이 있을 때 잔고 차감 및 포지션 갱신 확인")
    void should_create_market_buy_order_and_update_position_when_balance_is_sufficient() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.05"));
        BigDecimal currentPrice = new BigDecimal("100000000"); // 1억

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(valueOperations.get(anyString())).willReturn(currentPrice.toString());
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(virtualAccount));
        
        Position existingPosition = Position.builder()
                .user(testUser)
                .universalCode(marketCode)
                .avgBuyPrice(new BigDecimal("90000000"))
                .quantity(new BigDecimal("0.5"))
                .realizedPnl(BigDecimal.ZERO)
                .build();
        given(positionRepository.findByUserIdAndUniversalCode(userId, marketCode)).willReturn(Optional.of(existingPosition));

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(positionRepository.save(any(Position.class))).willAnswer(inv -> inv.getArgument(0));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        tradingService.createOrder(userId, request);

        // Assert
        assertThat(virtualAccount.getAvailableBalance().compareTo(new BigDecimal("4997500"))).isEqualTo(0);
        assertThat(existingPosition.getQuantity().compareTo(new BigDecimal("0.55"))).isEqualTo(0);
    }

    @Test
    @DisplayName("시장가 매수 정상 처리 - 잔고 차감 및 포지션 갱신 확인 (성공 케이스)")
    void should_create_market_buy_order_and_update_position_when_valid() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.05"));
        BigDecimal currentPrice = new BigDecimal("100000000"); // 1억

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(valueOperations.get(anyString())).willReturn(currentPrice.toString());
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(virtualAccount));
        given(positionRepository.findByUserIdAndUniversalCode(userId, marketCode)).willReturn(Optional.empty());

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(positionRepository.save(any(Position.class))).willAnswer(inv -> inv.getArgument(0));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        tradingService.createOrder(userId, request);

        // Assert
        assertThat(virtualAccount.getAvailableBalance().compareTo(new BigDecimal("4997500"))).isEqualTo(0);

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(positionRepository, atLeastOnce()).save(any(Position.class));
        verify(tradeRepository, atLeastOnce()).save(any(Trade.class));
    }

    @Test
    @DisplayName("시장가 매도 정상 처리 - 실현 손익 계산 및 잔고 증가 확인")
    void should_create_market_sell_order_and_calculate_pnl_when_valid() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.SELL, OrderType.MARKET, null, new BigDecimal("0.1"));
        BigDecimal currentPrice = new BigDecimal("100000000"); // 1억 현재가

        Position position = Position.builder()
                .user(testUser)
                .universalCode(marketCode)
                .avgBuyPrice(new BigDecimal("90000000")) // 9000만 평단
                .quantity(new BigDecimal("0.2")) // 0.2개 보유
                .realizedPnl(new BigDecimal("10000")) // 기존 실현 손익
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(valueOperations.get(anyString())).willReturn(currentPrice.toString());
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(virtualAccount));
        given(positionRepository.findByUserIdAndUniversalCode(userId, marketCode)).willReturn(Optional.of(position));

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(positionRepository.save(any(Position.class))).willAnswer(inv -> inv.getArgument(0));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        tradingService.createOrder(userId, request);

        // Assert
        assertThat(virtualAccount.getAvailableBalance().compareTo(new BigDecimal("19995000"))).isEqualTo(0);
        assertThat(position.getRealizedPnl().compareTo(new BigDecimal("1010000"))).isEqualTo(0);
        assertThat(position.getQuantity().compareTo(new BigDecimal("0.1"))).isEqualTo(0);

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(tradeRepository, atLeastOnce()).save(any(Trade.class));
    }

    @Test
    @DisplayName("지정가 매수 주문 - 정상적으로 잔고가 잠기고 Redis ZSet에 등록되어야 함")
    void should_create_limit_buy_order_and_lock_balance() {
        // Arrange
        BigDecimal price = new BigDecimal("100000000"); // 1억
        BigDecimal quantity = new BigDecimal("0.05"); // 0.05개
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.BUY, OrderType.LIMIT, price, quantity);

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(virtualAccount));
        
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

        // Act
        tradingService.createOrder(userId, request);

        // Assert
        assertThat(virtualAccount.getAvailableBalance().compareTo(new BigDecimal("4997500"))).isEqualTo(0);
        assertThat(virtualAccount.getLockedKrw().compareTo(new BigDecimal("5002500"))).isEqualTo(0);

        verify(orderRepository).save(any(Order.class));
        verify(zSetOperations).add("trading:limit-order:buy:KRW-BTC", "100", 100000000.0);
    }

    @Test
    @DisplayName("지정가 매도 주문 - 정상적으로 포지션 수량이 잠기고 Redis ZSet에 등록되어야 함")
    void should_create_limit_sell_order_and_lock_position_quantity() {
        // Arrange
        BigDecimal price = new BigDecimal("100000000"); // 1억
        BigDecimal quantity = new BigDecimal("0.1"); // 0.1개
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.SELL, OrderType.LIMIT, price, quantity);

        Position position = Position.builder()
                .user(testUser)
                .universalCode(marketCode)
                .avgBuyPrice(new BigDecimal("90000000"))
                .quantity(new BigDecimal("0.5"))
                .realizedPnl(BigDecimal.ZERO)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(positionRepository.findByUserIdAndUniversalCode(userId, marketCode)).willReturn(Optional.of(position));

        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 101L);
            return order;
        });

        // Act
        tradingService.createOrder(userId, request);

        // Assert
        assertThat(position.getAvailableQuantity().compareTo(new BigDecimal("0.4"))).isEqualTo(0);
        assertThat(position.getLockedQuantity().compareTo(new BigDecimal("0.1"))).isEqualTo(0);

        verify(orderRepository).save(any(Order.class));
        verify(zSetOperations).add("trading:limit-order:sell:KRW-BTC", "101", 100000000.0);
    }

    @Test
    @DisplayName("시장가 주문 실패 - 시장가인데 가격이 입력된 경우")
    void should_throw_exception_when_market_order_has_price() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.BUY, OrderType.MARKET, new BigDecimal("1000"), new BigDecimal("0.1"));

        // Act & Assert
        assertThatThrownBy(() -> tradingService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMON_INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("시장가 매수 실패 - 최소 주문 금액 미달")
    void should_throw_exception_when_order_amount_is_less_than_minimum() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(marketCode, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.00001"));
        BigDecimal currentPrice = new BigDecimal("100000000"); // 1억 * 0.00001 = 1000원

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(valueOperations.get(anyString())).willReturn(currentPrice.toString());
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(virtualAccount));

        // Act & Assert
        assertThatThrownBy(() -> tradingService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMMON_INVALID_INPUT.getMessage());
    }
}
