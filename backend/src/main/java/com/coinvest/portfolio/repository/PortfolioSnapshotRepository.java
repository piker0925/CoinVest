package com.coinvest.portfolio.repository;

import com.coinvest.portfolio.domain.PortfolioSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 포트폴리오 스냅샷 레포지토리.
 */
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    /**
     * 특정 날짜의 스냅샷 조회.
     */
    Optional<PortfolioSnapshot> findByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    /**
     * 목표일 이전 가장 가까운 스냅샷 조회 (Redis ZSet의 ZREVRANGEBYSCORE 동일 시맨틱).
     * 주말/공휴일에 과거 수익률 조회 시 가장 최근 영업일 스냅샷을 반환.
     */
    @Query("""
        SELECT s FROM PortfolioSnapshot s
        WHERE s.portfolio.id = :portfolioId
          AND s.snapshotDate <= :targetDate
        ORDER BY s.snapshotDate DESC
        """)
    List<PortfolioSnapshot> findClosestBefore(
        @Param("portfolioId") Long portfolioId,
        @Param("targetDate") LocalDate targetDate,
        Pageable pageable
    );

    /**
     * 특정 기간 내 스냅샷 목록 조회 (최신순).
     */
    @Query("""
        SELECT s FROM PortfolioSnapshot s
        WHERE s.portfolio.id = :portfolioId
          AND s.snapshotDate BETWEEN :startDate AND :endDate
        ORDER BY s.snapshotDate DESC
        """)
    List<PortfolioSnapshot> findByPortfolioIdAndDateRange(
        @Param("portfolioId") Long portfolioId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 날짜에 이미 스냅샷이 존재하는지 확인 (중복 캡처 방지).
     */
    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);
}
