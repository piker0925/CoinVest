package com.coinvest.trading.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "market_calendars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MarketCalendar extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Exchange exchange;

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Column(length = 100)
    private String description;

    public enum Exchange {
        UPBIT, KRX, NYSE
    }
}
