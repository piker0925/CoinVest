package com.coinvest.trading.repository;

import com.coinvest.trading.domain.Trade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    Slice<Trade> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Slice<Trade> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);
}
