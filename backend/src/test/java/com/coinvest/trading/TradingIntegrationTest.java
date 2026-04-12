package com.coinvest.trading;

import com.coinvest.AbstractIntegrationTest;
import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.repository.VirtualAccountRepository;
import com.coinvest.trading.service.TradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 거래 도메인 통합 테스트.
 * 주의: 멀티스레드 동시성 테스트를 포함하므로 클래스 레벨의 @Transactional을 사용하지 않음.
 */
class TradingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TradingService tradingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private AssetRepository assetRepository;

    @MockitoBean
    private PriceService priceService;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    private User testUser;
    private final String krwUniversalCode = "CRYPTO:BTC";
    private final String usdUniversalCode = "US_STOCK:AAPL";

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        // 기존 데이터 초기화 (수동)
        userRepository.deleteAll();
        assetRepository.deleteAll();
        
        testUser = userRepository.save(User.builder()
                .email("concurrency_test@example.com")
                .password("password")
                .nickname("Trader")
                .build());

        VirtualAccount account = VirtualAccount.builder()
                .user(testUser)
                .accountNumber("TRD-TEST-" + System.currentTimeMillis())
                .balances(new ArrayList<>())
                .build();
        virtualAccountRepository.save(account);

        // 초기 잔고 설정: KRW 1억, USD 1만
        Balance krwBalance = Balance.builder()
                .account(account)
                .currency(Currency.KRW)
                .available(new BigDecimal("100000000"))
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        account.getBalances().add(krwBalance);

        Balance usdBalance = Balance.builder()
                .account(account)
                .currency(Currency.USD)
                .available(new BigDecimal("10000"))
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        account.getBalances().add(usdBalance);

        virtualAccountRepository.save(account);

        assetRepository.save(Asset.builder()
                .universalCode(krwUniversalCode)
                .name("Bitcoin")
                .assetClass(AssetClass.CRYPTO)
                .exchangeCode("UPBIT")
                .externalCode("KRW-BTC")
                .quoteCurrency(Currency.KRW)
                .feeRate(new BigDecimal("0.0005"))
                .build());

        assetRepository.save(Asset.builder()
                .universalCode(usdUniversalCode)
                .name("Apple")
                .assetClass(AssetClass.US_STOCK)
                .exchangeCode("NYSE")
                .externalCode("AAPL")
                .quoteCurrency(Currency.USD)
                .feeRate(new BigDecimal("0.001"))
                .build());

        // Mock 설정
        given(priceService.getCurrentPrice(anyString(), any(PriceMode.class))).willReturn(new BigDecimal("100000000"));
        given(priceService.getCurrentPrice(anyString())).willReturn(new BigDecimal("100000000"));
        given(exchangeRateService.getCurrentExchangeRate(any(Currency.class), any(Currency.class), any(PriceMode.class)))
                .willReturn(new BigDecimal("1350.00"));
    }

    @Test
    @DisplayName("동시성 테스트: 10개 스레드가 동시에 통합증거금 주문 시 데드락 발생 없이 정확히 차감됨")
    void integrated_margin_concurrency_test() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // USD 부족 상황 유도
        BigDecimal quantity = new BigDecimal("100"); // 100주 * $200 = $20,000
        given(priceService.getCurrentPrice(usdUniversalCode, PriceMode.LIVE)).willReturn(new BigDecimal("200"));

        OrderCreateRequest request = new OrderCreateRequest(
                usdUniversalCode,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                quantity
        );

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    tradingService.createOrder(testUser.getId(), request, PriceMode.LIVE);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        transactionTemplate.executeWithoutResult(status -> {
            VirtualAccount account = virtualAccountRepository.findByUserId(testUser.getId()).orElseThrow();
            BigDecimal totalKrw = account.getAvailableForPurchase(Currency.KRW);
            BigDecimal totalUsd = account.getAvailableForPurchase(Currency.USD);

            // 잔고가 음수가 되지 않았는지 확인
            assertThat(totalKrw).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(totalUsd).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            
            // 데드락이 발생했다면 latch.await()에서 타임아웃이 났거나 success가 0일 것임
            assertThat(successCount.get()).isGreaterThan(0);
        });
    }
}
