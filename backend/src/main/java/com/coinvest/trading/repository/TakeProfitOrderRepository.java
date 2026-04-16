package com.coinvest.trading.repository;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.TakeProfitOrder;
import com.coinvest.trading.domain.StopLossTakeProfitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TakeProfitOrderRepository extends JpaRepository<TakeProfitOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tpo FROM TakeProfitOrder tpo JOIN FETCH tpo.position p " +
           "WHERE p.universalCode = :universalCode " +
           "AND p.priceMode = :mode " +
           "AND tpo.status = 'ACTIVE' " +
           "AND tpo.triggerPrice <= :currentPrice")
    List<TakeProfitOrder> findAllActiveTriggered(@Param("universalCode") String universalCode,
                                                 @Param("currentPrice") BigDecimal currentPrice,
                                                 @Param("mode") PriceMode mode);
}
