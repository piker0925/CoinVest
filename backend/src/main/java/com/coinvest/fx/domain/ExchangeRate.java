package com.coinvest.fx.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExchangeRate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency quoteCurrency;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;
}
