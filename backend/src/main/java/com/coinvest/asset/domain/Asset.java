package com.coinvest.asset.domain;

import com.coinvest.fx.domain.Currency;
import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Asset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String universalCode;

    @Column(nullable = false, length = 50)
    private String externalCode;

    @Column(length = 20)
    private String exchangeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetClass assetClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency quoteCurrency;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal feeRate;

    @Builder.Default
    @Column(nullable = false)
    private boolean isDemo = false;
}
