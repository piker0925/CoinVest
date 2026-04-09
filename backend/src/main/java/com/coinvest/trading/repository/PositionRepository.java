package com.coinvest.trading.repository;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByUserIdAndUniversalCode(Long userId, String universalCode);
    Optional<Position> findByUserIdAndUniversalCodeAndPriceMode(Long userId, String universalCode, PriceMode priceMode);
    List<Position> findAllByUserId(Long userId);
    List<Position> findByUserIdAndPriceMode(Long userId, PriceMode priceMode);
}
