package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.repository.*;
import com.coinvest.trading.service.strategy.TradingStrategy;
import com.coinvest.trading.service.strategy.TradingStrategyResolver;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    private SettlementRepository settlementRepository;

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

    @Mock
    private TradingStrategyResolver strategyResolver;

    @Mock
    private TradingStrategy tradingStrategy;

    @Mock
    private PortfolioRepository portfolioRepository;

    private User testUser;
    private VirtualAccount account;
    private Balance krwBalance;
    private Balance usdBalance;
    private final Long userId = 1L;
    private final String universalCode = "CRYPTO:BTC";

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(100L).email("test@example.com").role(UserRole.USER).nickname("Tester").build();
        ReflectionTestUtils.setField(testUser, "id", userId);

        account = VirtualAccount.builder()
                .id(10L)
                .user(testUser)
                .balances(new ArrayList<>())
                .build();
        
        krwBalance = Balance.builder()
                .account(account).currency(Currency.KRW).available(new BigDecimal("100000000")).build();
        usdBalance = Balance.builder()
                .account(account).currency(Currency.USD).available(BigDecimal.ZERO).build();
        
        account.getBalances().add(krwBalance);
        account.getBalances().add(usdBalance);

        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(virtualAccountRepository.findByUserId(userId)).willReturn(Optional.of(account));
        given(balanceRepository.findAllByAccountIdAndCurrenciesWithLock(anyLong(), anyList()))
                .willReturn(Arrays.asList(krwBalance, usdBalance));

        given(strategyResolver.resolve(any(PriceMode.class))).willReturn(tradingStrategy);
        given(tradingStrategy.getMode()).willReturn(PriceMode.LIVE);

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
        given(tradingStrategy.getCurrentPrice(universalCode)).willReturn(currentPrice);
        given(marketHoursService.isMarketOpen(btcAsset)).willReturn(true);
        
        // 실제 로직과 동일하게 lock() 호출
        given(marginCalculator.calculateAndApplyMargin(any(), any(), any(), any(PriceMode.class))).willAnswer(inv -> {
            BigDecimal amount = inv.getArgument(2);
            Balance bal = inv.getArgument(0);
            bal.lock(amount);
            return BigDecimal.ONE; 
        });

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> {
            Trade trade = inv.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", 1L);
            return trade;
        });

        // Act
        tradingService.createOrder(userId, request, PriceMode.LIVE);

        // Assert
        // 1억 - (500만 + 2500원) = 94,997,500
        BigDecimal expectedBalance = new BigDecimal("94997500");
        assertThat(krwBalance.getAvailable()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    @DisplayName("멀티스레드 동시 주문 시 데드락 없이 로직이 수행되어야 함")
    void should_handle_concurrent_orders_without_deadlock() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        OrderCreateRequest request = new OrderCreateRequest(universalCode, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.01"));
        Asset btcAsset = Asset.builder()
                .universalCode(universalCode).quoteCurrency(Currency.KRW).assetClass(AssetClass.CRYPTO).feeRate(new BigDecimal("0.0005")).build();

        given(assetRepository.findByUniversalCode(universalCode)).willReturn(Optional.of(btcAsset));
        given(tradingStrategy.getCurrentPrice(universalCode)).willReturn(new BigDecimal("100000000"));
        given(marketHoursService.isMarketOpen(btcAsset)).willReturn(true);
        
        // 실제 로직 모방: lock() 사용
        doAnswer(inv -> {
            BigDecimal amount = inv.getArgument(2);
            Balance bal = inv.getArgument(0);
            synchronized (bal) {
                bal.lock(amount);
            }
            return BigDecimal.ONE;
        }).when(marginCalculator).calculateAndApplyMargin(any(), any(), any(), any());

        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        given(tradeRepository.save(any(Trade.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    tradingService.createOrder(userId, request, PriceMode.LIVE);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(threadCount);
        // 차감액: 100만 + 500수수료 = 1,000,500
        // 10명 차감: 10,005,000
        // 잔고: 1억 - 10,005,000 = 89,995,000
        assertThat(krwBalance.getAvailable()).isEqualByComparingTo("89995000");
    }
}
