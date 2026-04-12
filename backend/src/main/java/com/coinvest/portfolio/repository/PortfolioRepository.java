package com.coinvest.portfolio.repository;

import com.coinvest.auth.domain.User;
import com.coinvest.portfolio.domain.Portfolio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 포트폴리오 레포지토리.
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    @EntityGraph(attributePaths = {"assets"})
    List<Portfolio> findAllByUser(User user);

    long countByUser(User user);

    /**
     * Daily Snapshot Job 청크 처리용 페이징 조회.
     * EntityGraph로 assets를 함께 로딩하여 N+1 방지.
     */
    @EntityGraph(attributePaths = {"assets", "user"})
    Slice<Portfolio> findAllBy(Pageable pageable);

    List<Portfolio> findByUserId(Long userId);

    Optional<Portfolio> findByIdAndUserId(Long id, Long userId);
}
