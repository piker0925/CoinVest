package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 엔티티.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "market_code", nullable = false)
    private String marketCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType type;

    /**
     * 주문 가격 (시장가 주문 시 null일 수 있음).
     */
    @Column(precision = 20, scale = 4)
    private BigDecimal price;

    /**
     * 주문 수량.
     */
    @Column(nullable = false, precision = 30, scale = 18)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /**
     * 체결 시각.
     */
    @Column(name = "filled_at")
    private LocalDateTime filledAt;

    /**
     * 주문 체결 처리.
     */
    public void fill() {
        this.status = OrderStatus.FILLED;
        this.filledAt = LocalDateTime.now();
    }

    /**
     * 주문 취소 처리.
     */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 주문 만료 처리.
     */
    public void expire() {
        this.status = OrderStatus.EXPIRED;
    }
}
