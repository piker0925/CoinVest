package com.coinvest.trading.repository;

import com.coinvest.trading.domain.MarketCalendar;
import com.coinvest.trading.domain.MarketCalendar.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MarketCalendarRepository extends JpaRepository<MarketCalendar, Long> {
    Optional<MarketCalendar> findByExchangeAndHolidayDate(Exchange exchange, LocalDate holidayDate);
    boolean existsByExchangeAndHolidayDate(Exchange exchange, LocalDate holidayDate);
}
