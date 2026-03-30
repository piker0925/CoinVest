package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 포지션 엔티티.
 * 사용자가 현재 보유 중인 자산(코인) 현황을 관리함.
 */
@Entity
@Table(name = "positions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "market_code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Position extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "market_code", nullable = false)
    private String marketCode;

    /**
     * 평균 매수 가격.
     */
    @Column(name = "avg_buy_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal avgBuyPrice;

    /**
     * 보유 수량.
     */
    @Column(nullable = false, precision = 30, scale = 18)
    private BigDecimal quantity;

    /**
     * 누적 실현 손익.
     */
    @Column(name = "realized_pnl", nullable = false, precision = 20, scale = 4)
    private BigDecimal realizedPnl;

    /**
     * 매수 시 포지션 갱신 (가중 평균가 계산).
     */
    public void addPosition(BigDecimal price, BigDecimal amount) {
        BigDecimal totalCost = this.avgBuyPrice.multiply(this.quantity)
                .add(price.multiply(amount));
        this.quantity = this.quantity.add(amount);
        
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.avgBuyPrice = totalCost.divide(this.quantity, 4, RoundingMode.HALF_UP);
        }
    }

    /**
     * 매도 시 포지션 갱신 및 실현 손익 누적.
     */
    public void subtractPosition(BigDecimal price, BigDecimal amount) {
        if (this.quantity.compareTo(amount) < 0) {
            throw new com.coinvest.global.exception.BusinessException(com.coinvest.global.exception.ErrorCode.TRADING_INSUFFICIENT_QUANTITY);
        }

        // 실현 손익 = (매도가 - 평단) * 매도수량
        BigDecimal pnl = price.subtract(this.avgBuyPrice).multiply(amount);
        this.realizedPnl = this.realizedPnl.add(pnl);
        
        this.quantity = this.quantity.subtract(amount);
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.avgBuyPrice = BigDecimal.ZERO;
        }
    }
}
