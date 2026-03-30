package com.coinvest.portfolio.domain;

import com.coinvest.auth.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 포트폴리오 레포지토리.
 */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    
    @EntityGraph(attributePaths = {"assets"})
    List<Portfolio> findAllByUser(User user);
    
    long countByUser(User user);
}
