package com.coinvest.trading.repository;

import com.coinvest.global.common.PriceMode;
import com.coinvest.trading.domain.Trade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    Slice<Trade> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Slice<Trade> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Trade t WHERE t.user.id = :userId AND t.priceMode = :priceMode")
    void deleteAllByUserIdAndPriceMode(@Param("userId") Long userId, @Param("priceMode") PriceMode priceMode);
}
