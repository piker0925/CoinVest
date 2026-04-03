package com.coinvest.portfolio.repository;

import com.coinvest.portfolio.domain.AlertSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertSettingRepository extends JpaRepository<AlertSetting, Long> {
    Optional<AlertSetting> findByPortfolioId(Long portfolioId);
}
