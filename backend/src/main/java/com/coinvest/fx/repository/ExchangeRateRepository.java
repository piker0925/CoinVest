package com.coinvest.fx.repository;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(Currency baseCurrency, Currency quoteCurrency);
}
