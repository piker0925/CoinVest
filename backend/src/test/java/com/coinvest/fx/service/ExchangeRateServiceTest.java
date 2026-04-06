package com.coinvest.fx.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.domain.ExchangeRate;
import com.coinvest.fx.repository.ExchangeRateRepository;
import com.coinvest.global.exception.CircuitBreakerException;
import com.coinvest.portfolio.service.DiscordClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private DiscordClient discordClient;

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("정상적인 환율 조회")
    void getCurrentExchangeRate_Success() {
        // given
        ExchangeRate rate = ExchangeRate.builder()
                .baseCurrency(Currency.USD)
                .quoteCurrency(Currency.KRW)
                .rate(new BigDecimal("1350.00"))
                .fetchedAt(LocalDateTime.now())
                .build();

        given(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency.USD, Currency.KRW))
                .willReturn(Optional.of(rate));

        // when
        BigDecimal result = exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW);

        // then
        assertThat(result).isEqualByComparingTo("1350.00");
    }

    @Test
    @DisplayName("48시간 이상 지난 환율 조회 시 CircuitBreakerException 발생")
    void getCurrentExchangeRate_Stale_ThrowsException() {
        // given
        ExchangeRate rate = ExchangeRate.builder()
                .baseCurrency(Currency.USD)
                .quoteCurrency(Currency.KRW)
                .rate(new BigDecimal("1350.00"))
                .fetchedAt(LocalDateTime.now().minusHours(49))
                .build();

        given(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency.USD, Currency.KRW))
                .willReturn(Optional.of(rate));

        // when & then
        assertThatThrownBy(() -> exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW))
                .isInstanceOf(CircuitBreakerException.class);
    }

    @Test
    @DisplayName("환율 정보가 없을 때 CircuitBreakerException 발생")
    void getCurrentExchangeRate_NotFound_ThrowsException() {
        // given
        given(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency.USD, Currency.KRW))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW))
                .isInstanceOf(CircuitBreakerException.class);
    }

    @Test
    @DisplayName("동일 통화 조회 시 1 반환")
    void getCurrentExchangeRate_SameCurrency_ReturnsOne() {
        // when
        BigDecimal result = exchangeRateService.getCurrentExchangeRate(Currency.KRW, Currency.KRW);

        // then
        assertThat(result).isEqualByComparingTo("1");
    }
}
