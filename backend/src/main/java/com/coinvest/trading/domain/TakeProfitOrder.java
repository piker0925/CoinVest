package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "take_profit_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TakeProfitOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal triggerPrice;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StopLossTakeProfitStatus status;

    public void process() {
        this.status = StopLossTakeProfitStatus.PROCESSING;
    }

    public void execute() {
        this.status = StopLossTakeProfitStatus.EXECUTED;
    }

    public void fail() {
        this.status = StopLossTakeProfitStatus.FAILED;
    }
}
