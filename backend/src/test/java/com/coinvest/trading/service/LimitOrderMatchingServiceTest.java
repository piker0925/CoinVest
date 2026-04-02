package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.TradeRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import com.coinvest.trading.dto.TradeEvent;
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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LimitOrderMatchingServiceTest {

    @InjectMocks
    private LimitOrderMatchingService matchingService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private User testUser;
    private Order buyOrder;
    private Order sellOrder;
    private VirtualAccount virtualAccount;
    private Position position;

    private final String marketCode = "KRW-BTC";

    @BeforeEach
    void setUp() {
        testUser = User.builder().build();
        ReflectionTestUtils.setField(testUser, "id", 1L);

        buyOrder = Order.builder()
                .user(testUser)
                .marketCode(marketCode)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(new BigDecimal("100000000"))
                .quantity(new BigDecimal("0.1"))
                .status(OrderStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(buyOrder, "id", 100L);

        sellOrder = Order.builder()
                .user(testUser)
                .marketCode(marketCode)
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .price(new BigDecimal("100000000"))
                .quantity(new BigDecimal("0.1"))
                .status(OrderStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(sellOrder, "id", 101L);

        virtualAccount = VirtualAccount.builder()
                .user(testUser)
                .balanceKrw(new BigDecimal("20005000")) // 가용(1000만) + 잠금(1000.5만)
                .lockedKrw(new BigDecimal("10005000")) // 1억 * 0.1 + 수수료
                .build();

        position = Position.builder()
                .user(testUser)
                .marketCode(marketCode)
                .avgBuyPrice(new BigDecimal("90000000"))
                .quantity(new BigDecimal("0.5"))
                .lockedQuantity(new BigDecimal("0.1"))
                .realizedPnl(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("지정가 매수 매칭 성공 - 현재가가 지정가 이하로 내려갔을 때 체결 및 Redis 클렌징")
    void should_match_and_execute_buy_order_when_price_drops() {
        // Arrange
        BigDecimal currentPrice = new BigDecimal("99000000"); // 1억 이하로 떨어짐
        String key = "trading:limit-order:buy:" + marketCode;

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(eq(key), anyDouble(), anyDouble()))
                .willReturn(Set.of("100"));

        given(orderRepository.updateStatusToFilledIfPending(100L)).willReturn(1);
        given(orderRepository.findById(100L)).willReturn(Optional.of(buyOrder));
        given(virtualAccountRepository.findByUserId(1L)).willReturn(Optional.of(virtualAccount));
        given(positionRepository.findByUserIdAndMarketCode(1L, marketCode)).willReturn(Optional.of(position));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        matchingService.matchOrders(marketCode, currentPrice);

        // Assert
        assertThat(virtualAccount.getBalanceKrw().compareTo(new BigDecimal("10100050"))).isEqualTo(0);
        assertThat(virtualAccount.getLockedKrw().compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(position.getQuantity().compareTo(new BigDecimal("0.6"))).isEqualTo(0);

        verify(tradeRepository, atLeastOnce()).save(any(Trade.class));
        verify(zSetOperations).remove(key, "100");
    }

    @Test
    @DisplayName("지정가 매도 매칭 성공 - 현재가가 지정가 이상으로 올랐을 때 체결 및 Redis 클렌징")
    void should_match_and_execute_sell_order_when_price_rises() {
        // Arrange
        BigDecimal currentPrice = new BigDecimal("110000000"); // 1억 이상으로 오름
        String key = "trading:limit-order:sell:" + marketCode;

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.rangeByScore(eq(key), anyDouble(), anyDouble()))
                .willReturn(Set.of("101"));

        given(orderRepository.updateStatusToFilledIfPending(101L)).willReturn(1);
        given(orderRepository.findById(101L)).willReturn(Optional.of(sellOrder));
        given(virtualAccountRepository.findByUserId(1L)).willReturn(Optional.of(virtualAccount));
        given(positionRepository.findByUserIdAndMarketCode(1L, marketCode)).willReturn(Optional.of(position));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        matchingService.matchOrders(marketCode, currentPrice);

        // Assert
        assertThat(position.getQuantity().compareTo(new BigDecimal("0.4"))).isEqualTo(0);
        assertThat(position.getLockedQuantity().compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(position.getRealizedPnl().compareTo(new BigDecimal("2000000"))).isEqualTo(0);

        verify(tradeRepository, atLeastOnce()).save(any(Trade.class));
        verify(zSetOperations).remove(key, "101");
    }

    @Test
    @DisplayName("조건부 업데이트 실패 시 (이미 취소되거나 체결된 주문) - Redis 클렌징(Self-healing)만 수행")
    void should_remove_from_redis_when_conditional_update_fails() {
        // Arrange
        BigDecimal currentPrice = new BigDecimal("99000000");
        String key = "trading:limit-order:buy:" + marketCode;

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(eq(key), anyDouble(), anyDouble()))
                .willReturn(Set.of("100"));

        given(orderRepository.updateStatusToFilledIfPending(100L)).willReturn(0);

        // Act
        matchingService.matchOrders(marketCode, currentPrice);

        // Assert
        verify(orderRepository, never()).findById(anyLong());
        verify(tradeRepository, never()).save(any());
        verify(zSetOperations).remove(key, "100");
    }
}
