package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioValuationServiceTest {

    @InjectMocks
    private PortfolioValuationService valuationService;

    @Mock
    private PriceService priceService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("정책 1-A 검증: 포트폴리오 가치 산출 시 순수 자산만 포함하고 계좌 현금은 제외해야 한다.")
    void evaluate_ExcludesCash() {
        // [Given]
        Long portfolioId = 1L;
        User user = User.builder().id(100L).role(UserRole.USER).build();
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getUser()).thenReturn(user);
        when(portfolio.getBaseCurrency()).thenReturn(Currency.KRW);
        
        PortfolioAsset btcAsset = PortfolioAsset.builder()
                .universalCode("CRYPTO:BTC").currency(Currency.KRW).quantity(BigDecimal.ONE).targetWeight(BigDecimal.ONE).build();
        when(portfolio.getAssets()).thenReturn(List.of(btcAsset));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        VirtualAccount account = mock(VirtualAccount.class);
        Balance krwBalance = Balance.builder().currency(Currency.KRW).available(new BigDecimal("1000000")).build();
        when(account.getBalances()).thenReturn(List.of(krwBalance));
        when(virtualAccountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));

        // PriceMode 인자를 포함한 스터빙으로 수정
        when(priceService.getPrices(anyList(), any(PriceMode.class)))
                .thenReturn(Map.of("CRYPTO:BTC", new BigDecimal("100000000")));
        
        // ExchangeRateService도 PriceMode 인자 포함 스터빙
        when(exchangeRateService.getExchangeRateWithStatus(any(), any(), any(PriceMode.class)))
                .thenReturn(new ExchangeRateService.ExchangeRateResponse(BigDecimal.ONE, false));
        
        when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));

        // [When]
        PortfolioValuation result = valuationService.evaluate(portfolioId, Currency.KRW);

        // [Then]
        assertThat(result.getTotalEvaluationBase()).isEqualByComparingTo("100000000");
    }

    @Test
    @DisplayName("정책 2-B 검증: Stale 환율 사용 시 응답에 플래그가 반영되어야 한다.")
    void evaluate_ReflectsStaleFxFlag() {
        // [Given]
        Long portfolioId = 1L;
        Portfolio portfolio = mock(Portfolio.class);
        User user = User.builder().id(1L).role(UserRole.USER).build();
        when(portfolio.getUser()).thenReturn(user);
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        
        PortfolioAsset usdAsset = PortfolioAsset.builder()
                .universalCode("US:AAPL").currency(Currency.USD).quantity(BigDecimal.ONE).build();
        when(portfolio.getAssets()).thenReturn(List.of(usdAsset));

        // PriceMode 인자 포함 스터빙
        when(priceService.getPrices(anyList(), any(PriceMode.class)))
                .thenReturn(Map.of("US:AAPL", new BigDecimal("200")));
        
        when(exchangeRateService.getExchangeRateWithStatus(any(), any(), any(PriceMode.class)))
                .thenReturn(new ExchangeRateService.ExchangeRateResponse(new BigDecimal("1400"), true));
        
        when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));

        // [When]
        PortfolioValuation result = valuationService.evaluate(portfolioId, Currency.KRW);

        // [Then]
        assertThat(result.isStaleExchangeRate()).isTrue();
    }
}
