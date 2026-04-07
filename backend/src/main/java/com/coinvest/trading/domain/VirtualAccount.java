package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 가상 계좌 엔티티.
 * 사용자의 가상 자금을 관리함. (Currency별 Balance 통합 관리)
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

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Balance> balances = new ArrayList<>();

    /**
     * 특정 통화의 잔고 조회.
     */
    public Balance getBalance(Currency currency) {
        return balances.stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRADING_BALANCE_NOT_FOUND));
    }

    /**
     * 특정 통화의 가용 매수 여력(Buying Power) 조회.
     */
    public BigDecimal getAvailableForPurchase(Currency currency) {
        return getBalance(currency).getAvailableForPurchase();
    }
}
