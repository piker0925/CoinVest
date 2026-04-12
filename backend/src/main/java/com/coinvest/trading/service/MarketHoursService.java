package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.trading.domain.MarketCalendar.Exchange;
import com.coinvest.trading.repository.MarketCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketHoursService {

    private final MarketCalendarRepository calendarRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId ET = ZoneId.of("America/New_York");

    private static final LocalTime KRX_OPEN = LocalTime.of(9, 0);
    private static final LocalTime KRX_CLOSE = LocalTime.of(15, 30);

    private static final LocalTime NYSE_OPEN = LocalTime.of(9, 30);
    private static final LocalTime NYSE_CLOSE = LocalTime.of(16, 0);

    /**
     * 자산별 거래소 개장 여부 확인.
     */
    public boolean isMarketOpen(Asset asset) {
        if (asset.getAssetClass() == AssetClass.CRYPTO || asset.getAssetClass() == AssetClass.VIRTUAL) {
            return true; // 코인 및 가상자산은 24/7
        }

        Exchange exchange = getExchangeFromAsset(asset);
        ZonedDateTime now = getZonedDateTime(exchange);
        
        // 1. 주말 체크
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        // 2. 휴장일 체크 (DB 조회)
        if (calendarRepository.existsByExchangeAndHolidayDate(exchange, now.toLocalDate())) {
            return false;
        }

        // 3. 시간대 체크
        LocalTime time = now.toLocalTime();
        if (exchange == Exchange.KRX) {
            return !time.isBefore(KRX_OPEN) && !time.isAfter(KRX_CLOSE);
        } else if (exchange == Exchange.NYSE) {
            return !time.isBefore(NYSE_OPEN) && !time.isAfter(NYSE_CLOSE);
        }

        return false;
    }

    /**
     * 정산 예정일(T+n) 계산.
     */
    public LocalDate calculateSettlementDate(Asset asset, LocalDate tradeDate) {
        if (asset.getAssetClass() == AssetClass.CRYPTO || asset.getAssetClass() == AssetClass.VIRTUAL) {
            return tradeDate; // 코인 및 가상자산은 T+0 (즉시 정산)
        }

        Exchange exchange = getExchangeFromAsset(asset);
        int daysToSettle = 2; // 주식/ETF는 T+2
        
        LocalDate settlementDate = tradeDate;
        int businessDaysAdded = 0;

        while (businessDaysAdded < daysToSettle) {
            settlementDate = settlementDate.plusDays(1);
            if (isBusinessDay(exchange, settlementDate)) {
                businessDaysAdded++;
            }
        }

        return settlementDate;
    }

    private boolean isBusinessDay(Exchange exchange, LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return !calendarRepository.existsByExchangeAndHolidayDate(exchange, date);
    }

    /**
     * 특정 날짜에 거래소가 개장했는지 확인 (시간과 무관).
     * LiveBenchmarkProvider의 Guard Clause에서 사용.
     * 주말 및 DB 등록 휴장일이면 false.
     */
    public boolean wasOpenOn(Exchange exchange, LocalDate date) {
        return isBusinessDay(exchange, date);
    }

    private Exchange getExchangeFromAsset(Asset asset) {
        return switch (asset.getAssetClass()) {
            case CRYPTO, VIRTUAL -> Exchange.UPBIT;
            case KR_STOCK, KR_ETF -> Exchange.KRX;
            case US_STOCK, US_ETF -> Exchange.NYSE;
        };
    }

    /**
     * 한국 거래소(KRX) 개장 여부 확인.
     */
    public boolean isKrxOpen() {
        return isExchangeOpen(Exchange.KRX);
    }

    /**
     * 뉴욕 거래소(NYSE) 개장 여부 확인.
     */
    public boolean isNyseOpen() {
        return isExchangeOpen(Exchange.NYSE);
    }

    private boolean isExchangeOpen(Exchange exchange) {
        if (exchange == Exchange.UPBIT) return true;

        ZonedDateTime now = getZonedDateTime(exchange);
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (calendarRepository.existsByExchangeAndHolidayDate(exchange, now.toLocalDate())) {
            return false;
        }

        LocalTime time = now.toLocalTime();
        if (exchange == Exchange.KRX) {
            return !time.isBefore(KRX_OPEN) && !time.isAfter(KRX_CLOSE);
        } else if (exchange == Exchange.NYSE) {
            return !time.isBefore(NYSE_OPEN) && !time.isAfter(NYSE_CLOSE);
        }
        return false;
    }

    private ZonedDateTime getZonedDateTime(Exchange exchange) {
        return switch (exchange) {
            case UPBIT, KRX -> ZonedDateTime.now(KST);
            case NYSE -> ZonedDateTime.now(ET);
        };
    }
}
