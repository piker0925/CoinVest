package com.coinvest.trading.repository;

import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = 'FILLED', o.filledAt = CURRENT_TIMESTAMP WHERE o.id = :id AND o.status = 'PENDING'")
    int updateStatusToFilledIfPending(@Param("id") Long id);

    Slice<Order> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    
    Slice<Order> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);
    
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    Slice<Order> findByStatusAndCreatedAtBefore(OrderStatus status, java.time.LocalDateTime createdAt, Pageable pageable);
}
