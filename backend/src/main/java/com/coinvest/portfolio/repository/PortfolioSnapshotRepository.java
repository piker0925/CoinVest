package com.coinvest.portfolio.repository;

import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    
    Optional<PortfolioSnapshot> findByPortfolioAndSnapshotDate(Portfolio portfolio, LocalDateTime snapshotDate);
    
    Optional<PortfolioSnapshot> findByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDateTime snapshotDate);
    
    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDateTime snapshotDate);

    @Query("SELECT s FROM PortfolioSnapshot s WHERE s.portfolio.id = :portfolioId AND s.snapshotDate <= :date ORDER BY s.snapshotDate DESC")
    List<PortfolioSnapshot> findClosestBefore(Long portfolioId, LocalDateTime date, Pageable pageable);

    List<PortfolioSnapshot> findByPortfolioAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(Portfolio portfolio, LocalDateTime snapshotDate);
    
    List<PortfolioSnapshot> findByPortfolioOrderBySnapshotDateAsc(Portfolio portfolio);
}
