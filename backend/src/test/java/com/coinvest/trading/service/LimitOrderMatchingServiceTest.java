package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.TradeRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import com.coinvest.trading.repository.BalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    private BalanceRepository balanceRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private User testUser;
    private VirtualAccount virtualAccount;
    private Balance krwBalance;
    private final String universalCode = "CRYPTO:BTC";

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("test@example.com").role(UserRole.ADMIN).build();
        virtualAccount = VirtualAccount.builder()
                .id(10L)
                .user(testUser)
                .balances(new ArrayList<>())
                .build();
        
        krwBalance = Balance.builder()
                .account(virtualAccount)
                .currency(Currency.KRW)
                .available(new BigDecimal("10000000"))
                .locked(new BigDecimal("10005000"))
                .unsettled(BigDecimal.ZERO)
                .build();
        virtualAccount.getBalances().add(krwBalance);

        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("지정가 매수 체결 - 잔고 잠금 해제 및 실체결 금액 차감 확인")
    void should_execute_buy_limit_order() {
        // given
        Long orderId = 100L;
        BigDecimal currentPrice = new BigDecimal("100000000"); // 1억
        Order order = Order.builder()
                .id(orderId)
                .user(testUser)
                .universalCode(universalCode)
                .currency(Currency.KRW)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(new BigDecimal("100000000"))
                .quantity(new BigDecimal("0.1"))
                .priceMode(PriceMode.LIVE)
                .build();

        given(orderRepository.updateStatusToFilledIfPending(orderId)).willReturn(1);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(virtualAccountRepository.findByUserId(testUser.getId())).willReturn(Optional.of(virtualAccount));
        given(balanceRepository.findByAccountIdAndCurrencyWithLock(anyLong(), any())).willReturn(Optional.of(krwBalance));
        given(positionRepository.findByUserIdAndUniversalCodeAndPriceMode(anyLong(), anyString(), any(PriceMode.class)))
                .willReturn(Optional.empty());
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        matchingService.executeBuyOrderInTransaction(orderId, currentPrice);

        // then
        assertThat(krwBalance.getAvailable().compareTo(new BigDecimal("10000000"))).isEqualTo(0);
        assertThat(krwBalance.getLocked().compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    @Test
    @DisplayName("지정가 매도 체결 - 포지션 해제 및 잔고 증가 확인")
    void should_execute_sell_limit_order() {
        // given
        Long orderId = 101L;
        BigDecimal currentPrice = new BigDecimal("100000000");
        Order order = Order.builder()
                .id(orderId)
                .user(testUser)
                .universalCode(universalCode)
                .currency(Currency.KRW)
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .quantity(new BigDecimal("0.1"))
                .priceMode(PriceMode.LIVE)
                .build();

        Position position = Position.builder()
                .user(testUser)
                .universalCode(universalCode)
                .avgBuyPrice(new BigDecimal("90000000"))
                .quantity(new BigDecimal("0.1"))
                .lockedQuantity(new BigDecimal("0.1"))
                .priceMode(PriceMode.LIVE)
                .build();

        given(orderRepository.updateStatusToFilledIfPending(orderId)).willReturn(1);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(virtualAccountRepository.findByUserId(testUser.getId())).willReturn(Optional.of(virtualAccount));
        given(balanceRepository.findByAccountIdAndCurrencyWithLock(anyLong(), any())).willReturn(Optional.of(krwBalance));
        given(positionRepository.findByUserIdAndUniversalCodeAndPriceMode(anyLong(), anyString(), any(PriceMode.class)))
                .willReturn(Optional.of(position));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        matchingService.executeSellOrderInTransaction(orderId, currentPrice);

        // then
        BigDecimal expectedReturn = new BigDecimal("9995000");
        assertThat(krwBalance.getAvailable().compareTo(new BigDecimal("19995000"))).isEqualTo(0);
        assertThat(position.getQuantity().compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }
}
