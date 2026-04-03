package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 가상 계좌 엔티티.
 * 사용자의 가상 자금을 관리함.
 */
@Entity
@Table(name = "virtual_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class VirtualAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * 총 잔고 (가용 + 잠금).
     */
    @Deprecated
    @Column(name = "balance_krw", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceKrw;

    /**
     * 잠금 잔고 (지정가 매수 주문 시 사용).
     */
    @Deprecated
    @Column(name = "locked_krw", nullable = false, precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal lockedKrw = BigDecimal.ZERO;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Balance> balances = new ArrayList<>();

    public Balance getBalance(Currency currency) {
        return balances.stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRADING_BALANCE_NOT_FOUND));
    }

    public BigDecimal getAvailableForPurchase(Currency currency) {
        return getBalance(currency).getAvailableForPurchase();
    }

    /**
     * 가용 잔고 조회.
     * @deprecated 다중 통화 구조 도입에 따라 getAvailableBalance(Currency) 사용 권장
     */
    @Deprecated
    public BigDecimal getAvailableBalance() {
        return balanceKrw.subtract(lockedKrw);
    }

    /**
     * 잔고 차감 (매수 시).
     * @deprecated 다중 통화 구조 도입에 따라 Balance.withdraw() 사용 권장
     */
    @Deprecated
    public void decreaseBalance(BigDecimal amount) {
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
        }
        this.balanceKrw = this.balanceKrw.subtract(amount);
    }

    /**
     * 잔고 증가 (매도 시).
     * @deprecated 다중 통화 구조 도입에 따라 Balance.deposit() 사용 권장
     */
    @Deprecated
    public void increaseBalance(BigDecimal amount) {
        this.balanceKrw = this.balanceKrw.add(amount);
    }

    /**
     * 지정가 주문을 위한 잔고 잠금.
     * @deprecated 다중 통화 구조 도입에 따라 Balance.lock() 사용 권장
     */
    @Deprecated
    public void lockBalance(BigDecimal amount) {
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
        }
        this.lockedKrw = this.lockedKrw.add(amount);
    }

    /**
     * 지정가 체결/취소 시 잠금 해제.
     * @deprecated 다중 통화 구조 도입에 따라 Balance.unlock() 사용 권장
     */
    @Deprecated
    public void unlockBalance(BigDecimal amount) {
        this.lockedKrw = this.lockedKrw.subtract(amount);
    }
}
