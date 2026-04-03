package com.coinvest.portfolio.repository;

import com.coinvest.portfolio.domain.AlertHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

    @Query("SELECT h FROM AlertHistory h " +
            "WHERE h.portfolio.id = :portfolioId " +
            "AND (:cursorId IS NULL OR h.id < :cursorId) " +
            "ORDER BY h.id DESC")
    Slice<AlertHistory> findByPortfolioId(@Param("portfolioId") Long portfolioId,
                                          @Param("cursorId") Long cursorId,
                                          Pageable pageable);
}
