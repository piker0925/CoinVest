package com.coinvest.trading.repository;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.StopLossOrder;
import com.coinvest.trading.domain.StopLossTakeProfitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface StopLossOrderRepository extends JpaRepository<StopLossOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slo FROM StopLossOrder slo JOIN FETCH slo.position p " +
           "WHERE p.universalCode = :universalCode " +
           "AND p.priceMode = :mode " +
           "AND slo.status = 'ACTIVE' " +
           "AND slo.triggerPrice >= :currentPrice")
    List<StopLossOrder> findAllActiveTriggered(@Param("universalCode") String universalCode,
                                               @Param("currentPrice") BigDecimal currentPrice,
                                               @Param("mode") PriceMode mode);
}
