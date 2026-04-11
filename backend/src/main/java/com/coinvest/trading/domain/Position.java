package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 포지션 엔티티.
 */
@Entity
@Table(name = "positions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "universal_code", "price_mode"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Position extends BaseEntity {

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
    @Column(name = "price_mode", nullable = false, length = 20)
    @Builder.Default
    private PriceMode priceMode = PriceMode.LIVE;

    @Column(name = "avg_buy_price", nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal avgBuyPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal quantity;

    @Column(name = "locked_quantity", nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal lockedQuantity = BigDecimal.ZERO;

    @Column(name = "realized_pnl", nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    public BigDecimal getAvailableQuantity() {
        return quantity.subtract(lockedQuantity);
    }

    public void lockQuantity(BigDecimal amount) {
        if (getAvailableQuantity().compareTo(amount) < 0) {
            throw new com.coinvest.global.exception.BusinessException(com.coinvest.global.exception.ErrorCode.TRADING_INSUFFICIENT_QUANTITY);
        }
        this.lockedQuantity = this.lockedQuantity.add(amount);
    }

    public void unlockQuantity(BigDecimal amount) {
        this.lockedQuantity = this.lockedQuantity.subtract(amount);
    }

    public void addPosition(BigDecimal price, BigDecimal amount) {
        BigDecimal totalCost = this.avgBuyPrice.multiply(this.quantity)
                .add(price.multiply(amount));
        this.quantity = this.quantity.add(amount);
        
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.avgBuyPrice = totalCost.divide(this.quantity, 8, RoundingMode.HALF_UP);
        }
    }

    public void subtractPosition(BigDecimal price, BigDecimal amount) {
        if (getAvailableQuantity().compareTo(amount) < 0) {
            throw new com.coinvest.global.exception.BusinessException(com.coinvest.global.exception.ErrorCode.TRADING_INSUFFICIENT_QUANTITY);
        }

        BigDecimal pnl = price.subtract(this.avgBuyPrice).multiply(amount);
        this.realizedPnl = this.realizedPnl.add(pnl);
        
        this.quantity = this.quantity.subtract(amount);
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.avgBuyPrice = BigDecimal.ZERO;
        }
    }
}
