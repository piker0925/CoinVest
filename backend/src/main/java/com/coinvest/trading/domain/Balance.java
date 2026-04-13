package com.coinvest.trading.domain;

import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "balances",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"account_id", "currency"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Balance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private VirtualAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal available = BigDecimal.ZERO;

    @Column(nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal locked = BigDecimal.ZERO;

    @Column(nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal unsettled = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version;

    public void decreaseAvailable(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
        }
        this.available = this.available.subtract(amount);
    }

    public void increaseAvailable(BigDecimal amount) {
        this.available = this.available.add(amount);
    }

    public void increaseUnsettled(BigDecimal amount) {
        this.unsettled = this.unsettled.add(amount);
    }

    public void decreaseUnsettled(BigDecimal amount) {
        if (unsettled.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
        }
        this.unsettled = this.unsettled.subtract(amount);
    }

    public void withdraw(BigDecimal amount) {
        decreaseAvailable(amount);
    }

    public void deposit(BigDecimal amount) {
        increaseAvailable(amount);
    }

    public void lock(BigDecimal amount) {
        if (available.compareTo(amount) >= 0) {
            this.available = this.available.subtract(amount);
            this.locked = this.locked.add(amount);
        } else {
            BigDecimal shortage = amount.subtract(available);
            if (unsettled.compareTo(shortage) < 0) {
                throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
            }
            this.locked = this.locked.add(amount);
            this.unsettled = this.unsettled.subtract(shortage);
            this.available = BigDecimal.ZERO;
        }
    }

    public void unlock(BigDecimal amount) {
        this.locked = this.locked.subtract(amount);
        this.available = this.available.add(amount);
    }

    public void addUnsettled(BigDecimal amount) {
        increaseUnsettled(amount);
    }

    public void settle(BigDecimal amount) {
        this.unsettled = this.unsettled.subtract(amount);
        this.available = this.available.add(amount);
    }

    public BigDecimal getAvailableForPurchase() {
        return available.add(unsettled);
    }

    public BigDecimal getTotal() {
        return available.add(locked);
    }
    
    public void unsettledWithdraw(BigDecimal amount) {
        if (unsettled.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
        }
        this.unsettled = this.unsettled.subtract(amount);
    }

    /**
     * 계좌 초기화 전용: 잔고 전체를 지정 금액으로 덮어씌움.
     * resetAccount()에서만 사용. 개별 주문 취소 경로에서는 사용 금지.
     */
    public void resetTo(BigDecimal amount) {
        this.available = amount;
        this.locked = BigDecimal.ZERO;
        this.unsettled = BigDecimal.ZERO;
    }
}
