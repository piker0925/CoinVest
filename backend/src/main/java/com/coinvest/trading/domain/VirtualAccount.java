package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * 총 잔고 (가용 + 잠금).
     */
    @Column(name = "balance_krw", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceKrw;

    /**
     * 잠금 잔고 (지정가 매수 주문 시 사용).
     */
    @Column(name = "locked_krw", nullable = false, precision = 20, scale = 4)
    private BigDecimal lockedKrw;

    /**
     * 가용 잔고 조회.
     */
    public BigDecimal getAvailableBalance() {
        return balanceKrw.subtract(lockedKrw);
    }

    /**
     * 잔고 차감 (매수 시).
     */
    public void decreaseBalance(BigDecimal amount) {
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT); // 추후 INSUFFICIENT_BALANCE 추가 필요
        }
        this.balanceKrw = this.balanceKrw.subtract(amount);
    }

    /**
     * 잔고 증가 (매도 시).
     */
    public void increaseBalance(BigDecimal amount) {
        this.balanceKrw = this.balanceKrw.add(amount);
    }

    /**
     * 지정가 주문을 위한 잔고 잠금.
     */
    public void lockBalance(BigDecimal amount) {
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }
        this.lockedKrw = this.lockedKrw.add(amount);
    }

    /**
     * 지정가 체결/취소 시 잠금 해제.
     */
    public void unlockBalance(BigDecimal amount) {
        this.lockedKrw = this.lockedKrw.subtract(amount);
    }
}
