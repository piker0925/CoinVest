package com.coinvest.trading.repository;

import com.coinvest.fx.domain.Currency;
import com.coinvest.trading.domain.Balance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Balance b WHERE b.accountId = :accountId AND b.currency = :currency")
    Optional<Balance> findByAccountIdAndCurrencyWithLock(@Param("accountId") Long accountId, @Param("currency") Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Balance b WHERE b.accountId = :accountId AND b.currency IN :currencies ORDER BY b.currency ASC")
    List<Balance> findAllByAccountIdAndCurrenciesWithLock(@Param("accountId") Long accountId, @Param("currencies") List<Currency> currencies);

    List<Balance> findAllByAccountId(Long accountId);
}
