package com.coinvest.trading.repository;

import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.domain.Settlement.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findAllByStatusAndSettlementDateBefore(SettlementStatus status, LocalDate date);
    List<Settlement> findAllByStatusAndSettlementDate(SettlementStatus status, LocalDate date);
}
