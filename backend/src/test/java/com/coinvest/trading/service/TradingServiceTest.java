package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.repository.*;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
    private BalanceRepository balanceRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private PriceService priceService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private MarketHoursService marketHoursService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private MarginCalculator marginCalculator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private User testUser;
    private VirtualAccount account;
    private Balance krwBalance;
    private Balance usdBalance;
    private final Long userId = 1L;
    private final String universalCode = "CRYPTO:BTC";

    @BeforeEach
    void setUp() {
        testUser = User.builder().email("test@example.com").nickname("Tester").build();
        ReflectionTestUtils.setField(testUser, "id", userId);

        account = VirtualAccount.builder()
                .id(10L)
                .user(testUser)
                .balances(new ArrayList<>())
                .build();
        
        krwBalance = Balance.builder()
                .account(account).currency(Currency.KRW).available(new BigDecimal("10000000")).build();
        usdBalance = Balance.builder()
                .account(account).currency(Currency.USD).available(BigDecimal.ZERO).build();
        
        account.getBalances().add(krwBalance);
        account.getBalances().add(usdBalance);

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(account));
        // 중요: 리포지토리가 항상 setUp에서 생성한 동일한 인스턴스 리스트를 반환하도록 함
        given(balanceRepository.findAllByAccountIdAndCurrenciesWithLock(anyLong(), anyList()))
                .willReturn(Arrays.asList(krwBalance, usdBalance));

        given(redisTemplate.opsForValue()).willReturn(mock(ValueOperations.class));
        given(redisTemplate.opsForZSet()).willReturn(mock(org.springframework.data.redis.core.ZSetOperations.class));
    }

    @Test
    @DisplayName("시장가 매수 정상 처리 - 잔고 차감 확인")
    void should_create_market_buy_order_and_update_position_when_valid() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(universalCode, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.05"));
        BigDecimal currentPrice = new BigDecimal("100000000"); // 1억

        Asset btcAsset = Asset.builder()
                .universalCode(universalCode).quoteCurrency(Currency.KRW).assetClass(AssetClass.CRYPTO).feeRate(new BigDecimal("0.0005")).build();

        given(assetRepository.findByUniversalCode(universalCode)).willReturn(Optional.of(btcAsset));
        given(priceService.getCurrentPrice(universalCode)).willReturn(currentPrice);
        given(marketHoursService.isMarketOpen(btcAsset)).willReturn(true);
        
        // MarginCalculator가 실제로 잔고를 차감하도록 유도하거나 결과를 모킹
        given(marginCalculator.calculateAndApplyMargin(any(), any(), any())).willAnswer(inv -> {
            BigDecimal totalAmount = currentPrice.multiply(request.quantity());
            BigDecimal fee = totalAmount.multiply(new BigDecimal("0.0005"));
            krwBalance.decreaseAvailable(totalAmount.add(fee));
            return totalAmount.add(fee);
        });

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> {
            Trade trade = inv.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", 1L);
            return trade;
        });

        // Act
        tradingService.createOrder(userId, request);

        // Assert
        // 500만 + 2500원 차감 -> 4,997,500
        BigDecimal expectedBalance = new BigDecimal("4997500");
        assertThat(krwBalance.getAvailable()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    @DisplayName("시장가 주문 실패 - 주문 검증기에서 에러 발생 시 전파되어야 함")
    void should_throw_exception_when_validator_fails() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(universalCode, OrderSide.BUY, OrderType.MARKET, null, BigDecimal.ONE);
        doThrow(new BusinessException(ErrorCode.COMMON_INVALID_INPUT))
                .when(orderValidator).validateRequest(any());

        // Act & Assert
        assertThatThrownBy(() -> tradingService.createOrder(userId, request))
                .isInstanceOf(BusinessException.class);
    }
}
