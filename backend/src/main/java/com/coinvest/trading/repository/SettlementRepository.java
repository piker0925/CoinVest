package com.coinvest.trading.repository;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.domain.Settlement.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findAllByStatusAndSettlementDateBefore(SettlementStatus status, LocalDate date);
    List<Settlement> findAllByStatusAndSettlementDate(SettlementStatus status, LocalDate date);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Settlement s WHERE s.user.id = :userId AND s.priceMode = :priceMode")
    void deleteAllByUserIdAndPriceMode(@Param("userId") Long userId, @Param("priceMode") PriceMode priceMode);
}
