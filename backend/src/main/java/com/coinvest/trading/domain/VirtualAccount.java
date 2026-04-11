package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 가상 계좌 엔티티.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true, length = 50)
    private String accountNumber;

    /**
     * 총 순 입금액 (원금). ROI 계산의 기준이 됨.
     */
    @Column(name = "total_net_contribution", nullable = false, precision = 38, scale = 20)
    @Builder.Default
    private BigDecimal totalNetContribution = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Balance> balances = new ArrayList<>();

    public void updateContribution(BigDecimal amount) {
        this.totalNetContribution = this.totalNetContribution.add(amount);
    }

    public void deactivate() {
        this.isActive = false;
    }
}
