package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.AuthProvider;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PortfolioValuationIntegrationTest {

    @Autowired
    private PortfolioValuationService valuationService;

    @MockBean
    private PriceService priceService;

    @MockBean
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

    @BeforeEach
    void setUp() {
        // 1. 유저 생성 (JPA 레벨에서 확실히)
        User user = User.builder()
                .email("valuation@test.com")
                .password("password")
                .nickname("tester")
                .role(UserRole.USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();
        userRepository.save(user);

        // 2. 가상 계좌 생성
        VirtualAccount account = VirtualAccount.builder()
                .user(user)
                .balances(new ArrayList<>())
                .build();
        virtualAccountRepository.save(account);

        // 3. 포트폴리오 생성
        Portfolio portfolio = Portfolio.builder()
                .name("Test Portfolio")
                .initialInvestment(new BigDecimal("1000000"))
                .baseCurrency(Currency.KRW)
                .user(user)
                .build();
        portfolioRepository.save(portfolio);
        
        this.testPortfolioId = portfolio.getId();
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("단일 자산 평가 로직 검증")
    void evaluate_Logic_Success() {
        // [Given]
        Portfolio portfolio = portfolioRepository.findById(testPortfolioId).orElseThrow();
        
        // PortfolioAsset을 생성할 때 수동으로 연관 관계 설정 (protected 메서드 대신 필드 직접 주입 시뮬레이션)
        PortfolioAsset asset = PortfolioAsset.builder()
                .universalCode("CRYPTO:BTC")
                .currency(Currency.KRW)
                .quantity(new BigDecimal("1.0"))
                .targetWeight(BigDecimal.ONE)
                .build();
        
        portfolio.addAsset(asset);
        portfolioRepository.saveAndFlush(portfolio);

        // Mock
        when(priceService.getPrices(anyList())).thenReturn(Map.of("CRYPTO:BTC", new BigDecimal("100000000")));
        when(exchangeRateService.getExchangeRateWithStatus(any(), any()))
                .thenReturn(new ExchangeRateService.ExchangeRateResponse(BigDecimal.ONE, false));

        // [When]
        PortfolioValuation result = valuationService.evaluate(testPortfolioId, Currency.KRW);

        // [Then]
        assertThat(result.getTotalEvaluationBase()).isEqualByComparingTo("100000000");
    }
}
