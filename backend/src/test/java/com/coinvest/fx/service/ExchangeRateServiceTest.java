package com.coinvest.fx.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.domain.ExchangeRate;
import com.coinvest.fx.repository.ExchangeRateRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.exception.CircuitBreakerException;
import com.coinvest.portfolio.service.DiscordClient;
import com.coinvest.price.service.KisApiManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private DiscordClient discordClient;

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KisApiManager kisApiManager;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("정상적인 환율 조회 (LIVE 모드, DB 조회)")
    void getExchangeRateWithStatus_Live_Success() {
        // given
        ExchangeRate rate = ExchangeRate.builder()
                .baseCurrency(Currency.USD)
                .quoteCurrency(Currency.KRW)
                .rate(new BigDecimal("1350.00"))
                .fetchedAt(LocalDateTime.now())
                .build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency.USD, Currency.KRW))
                .willReturn(Optional.of(rate));

        // when
        ExchangeRateService.ExchangeRateResponse result = exchangeRateService.getExchangeRateWithStatus(Currency.USD, Currency.KRW, PriceMode.LIVE);

        // then
        assertThat(result.rate()).isEqualByComparingTo("1350.00");
        assertThat(result.isStale()).isFalse();
    }

    @Test
    @DisplayName("48시간 이상 지난 환율 조회 시 isStale=true 반환 (LIVE 모드)")
    void getExchangeRateWithStatus_Live_Stale() {
        // given
        ExchangeRate rate = ExchangeRate.builder()
                .baseCurrency(Currency.USD)
                .quoteCurrency(Currency.KRW)
                .rate(new BigDecimal("1350.00"))
                .fetchedAt(LocalDateTime.now().minusHours(49))
                .build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency.USD, Currency.KRW))
                .willReturn(Optional.of(rate));

        // when
        ExchangeRateService.ExchangeRateResponse result = exchangeRateService.getExchangeRateWithStatus(Currency.USD, Currency.KRW, PriceMode.LIVE);

        // then
        assertThat(result.isStale()).isTrue();
    }

    @Test
    @DisplayName("엄격한 환율 조회 시 만료 데이터는 CircuitBreakerException 발생")
    void getCurrentExchangeRate_Stale_ThrowsException() {
        // given
        ExchangeRate rate = ExchangeRate.builder()
                .baseCurrency(Currency.USD)
                .quoteCurrency(Currency.KRW)
                .rate(new BigDecimal("1350.00"))
                .fetchedAt(LocalDateTime.now().minusHours(49))
                .build();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency.USD, Currency.KRW))
                .willReturn(Optional.of(rate));

        // when & then
        assertThatThrownBy(() -> exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW, PriceMode.LIVE))
                .isInstanceOf(CircuitBreakerException.class);
    }

    @Test
    @DisplayName("DEMO 모드에서 Redis에 환율이 없으면 CircuitBreakerException 발생")
    void getExchangeRateWithStatus_Demo_CacheMiss_ThrowsException() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        // when & then
        assertThatThrownBy(() -> exchangeRateService.getExchangeRateWithStatus(Currency.USD, Currency.KRW, PriceMode.DEMO))
                .isInstanceOf(CircuitBreakerException.class);
    }

    @Test
    @DisplayName("동일 통화 조회 시 1 반환 및 isStale=false")
    void getExchangeRateWithStatus_SameCurrency_ReturnsOne() {
        // when
        ExchangeRateService.ExchangeRateResponse result = exchangeRateService.getExchangeRateWithStatus(Currency.KRW, Currency.KRW, PriceMode.LIVE);

        // then
        assertThat(result.rate()).isEqualByComparingTo("1");
        assertThat(result.isStale()).isFalse();
    }
}
