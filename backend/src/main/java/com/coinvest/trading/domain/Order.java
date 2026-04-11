package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
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

    @Column(name = "universal_code", nullable = false, length = 50)
    private String universalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    @Builder.Default
    private PriceMode priceMode = PriceMode.LIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType type;

    @Column(precision = 38, scale = 20) // 스키마와 일치하도록 수정
    private BigDecimal price;

    @Column(nullable = false, precision = 38, scale = 20) // 스키마와 일치하도록 수정
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private boolean reservation;

    @Column(name = "reservation_triggered_at")
    private LocalDateTime reservationTriggeredAt;

    @Column(name = "filled_at")
    private LocalDateTime filledAt;

    public void triggerReservation() {
        this.reservationTriggeredAt = LocalDateTime.now();
    }

    public void fill() {
        this.status = OrderStatus.FILLED;
        this.filledAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void expire() {
        this.status = OrderStatus.EXPIRED;
    }
}
