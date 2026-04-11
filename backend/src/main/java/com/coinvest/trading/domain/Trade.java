package com.coinvest.trading.domain;

import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import com.coinvest.global.common.PriceMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 체결 내역 엔티티.
 */
@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Trade extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "universal_code", nullable = false, length = 50)
    private String universalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(name = "exchange_rate_snapshot", precision = 20, scale = 6) // DB 스키마에 맞춰 (20, 6)으로 수정
    private BigDecimal exchangeRateSnapshot;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal price;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal fee;

    @Column(name = "realized_pnl", nullable = false, precision = 38, scale = 20)
    private BigDecimal realizedPnl;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 20)
    @Builder.Default
    private PriceMode priceMode = PriceMode.LIVE;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;
}
