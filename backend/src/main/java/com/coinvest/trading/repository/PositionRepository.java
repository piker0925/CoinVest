package com.coinvest.trading.repository;

import com.coinvest.trading.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByUserIdAndUniversalCode(Long userId, String universalCode);
    List<Position> findAllByUserId(Long userId);
}
