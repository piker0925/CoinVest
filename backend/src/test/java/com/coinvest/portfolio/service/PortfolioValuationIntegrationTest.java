package com.coinvest.portfolio.service;

import com.coinvest.AbstractIntegrationTest;
import com.coinvest.auth.domain.AuthProvider;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 포트폴리오 가치 평가 통합 테스트.
 */
@Transactional
class PortfolioValuationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PortfolioValuationService valuationService;

    @MockitoBean
    private PriceService priceService;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private Long testPortfolioId;
    private User testUser;

    @BeforeEach
    void setUp() {
        // 1. 유저 생성
        testUser = User.builder()
                .email("valuation@test.com")
                .password("password")
                .nickname("tester")
                .role(UserRole.USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();
        userRepository.save(testUser);

        // 2. 가상 계좌 및 잔고 생성
        VirtualAccount account = VirtualAccount.builder()
                .user(testUser)
                .balances(new ArrayList<>())
                .build();
        virtualAccountRepository.save(account);

        Balance krwBalance = Balance.builder()
                .account(account)
                .currency(Currency.KRW)
                .available(BigDecimal.ZERO)
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        account.getBalances().add(krwBalance);
        virtualAccountRepository.save(account);

        // 3. 포트폴리오 생성 (PriceMode.DEMO 자동 할당 예정 - Role=USER)
        Portfolio portfolio = Portfolio.builder()
                .name("Test Portfolio")
                .initialInvestment(new BigDecimal("1000000"))
                .baseCurrency(Currency.KRW)
                .user(testUser)
                .priceMode(PriceMode.DEMO)
                .build();
        portfolioRepository.save(portfolio);
        
        this.testPortfolioId = portfolio.getId();
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("혼합 자산 포트폴리오 평가 시 기준 통화(KRW)로 정확히 환산되어야 하며, 계좌 현금은 제외되어야 한다.")
    void evaluate_MixedAssets_ExcludingCash() {
        // [Given]
        Portfolio portfolio = portfolioRepository.findById(testPortfolioId).orElseThrow();
        
        PortfolioAsset btcAsset = PortfolioAsset.builder()
                .universalCode("CRYPTO:BTC").currency(Currency.KRW).quantity(new BigDecimal("1.0")).targetWeight(new BigDecimal("0.5")).build();
        PortfolioAsset aaplAsset = PortfolioAsset.builder()
                .universalCode("US_STOCK:AAPL").currency(Currency.USD).quantity(new BigDecimal("10.0")).targetWeight(new BigDecimal("0.5")).build();
        
        portfolio.addAsset(btcAsset);
        portfolio.addAsset(aaplAsset);
        portfolioRepository.saveAndFlush(portfolio);

        // 현금 1,000,000 KRW 추가 (평가액에 미포함 확인용 - Policy 1-A)
        VirtualAccount account = virtualAccountRepository.findByUserId(portfolio.getUser().getId()).orElseThrow();
        Balance krwBalance = account.getBalance(Currency.KRW);
        krwBalance.deposit(new BigDecimal("1000000"));
        virtualAccountRepository.saveAndFlush(account);

        // Mock Prices & FX (PriceMode 인자 추가 반영)
        when(priceService.getPrices(anyList(), any(PriceMode.class))).thenReturn(Map.of(
                "CRYPTO:BTC", new BigDecimal("100000000"),
                "US_STOCK:AAPL", new BigDecimal("200")
        ));
        
        when(exchangeRateService.getExchangeRateWithStatus(any(Currency.class), any(Currency.class), any(PriceMode.class)))
                .thenAnswer(invocation -> {
                    Currency base = invocation.getArgument(0);
                    Currency quote = invocation.getArgument(1);
                    if (base == quote) return new ExchangeRateService.ExchangeRateResponse(BigDecimal.ONE, false);
                    if (base == Currency.USD && quote == Currency.KRW) 
                        return new ExchangeRateService.ExchangeRateResponse(new BigDecimal("1400"), false);
                    return null;
                });

        // [When]
        PortfolioValuation result = valuationService.evaluate(testPortfolioId, Currency.KRW);

        // [Then]
        // BTC (100M) + AAPL (10.0*200*1400 = 2.8M) = 102,800,000 (현금 1M 제외 확인)
        assertThat(result.getTotalEvaluationBase()).isEqualByComparingTo("102800000");
        assertThat(result.isStaleExchangeRate()).isFalse();
    }

    @Test
    @DisplayName("환율 정보가 지연(Stale)되었을 때 에러 없이 결과에 플래그가 표시되어야 한다.")
    void evaluate_WithStaleExchangeRate() {
        // [Given]
        Portfolio portfolio = portfolioRepository.findById(testPortfolioId).orElseThrow();
        portfolio.addAsset(PortfolioAsset.builder()
                .universalCode("US_STOCK:AAPL")
                .currency(Currency.USD)
                .quantity(BigDecimal.ONE)
                .targetWeight(BigDecimal.ONE)
                .build());
        portfolioRepository.saveAndFlush(portfolio);

        when(priceService.getPrices(anyList(), any(PriceMode.class))).thenReturn(Map.of("US_STOCK:AAPL", new BigDecimal("200")));
        when(exchangeRateService.getExchangeRateWithStatus(any(), any(), any()))
                .thenReturn(new ExchangeRateService.ExchangeRateResponse(new BigDecimal("1400"), true));

        // [When]
        PortfolioValuation result = valuationService.evaluate(testPortfolioId, Currency.KRW);

        // [Then]
        assertThat(result.isStaleExchangeRate()).isTrue();
    }
}
