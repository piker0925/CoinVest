-- V10: market_calendars 테이블 생성 (거래소 휴장일 관리)
CREATE TABLE market_calendars (
    id BIGSERIAL PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL, -- UPBIT, KRX, NYSE
    holiday_date DATE NOT NULL,
    description VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_market_calendar_date UNIQUE (exchange, holiday_date)
);

CREATE INDEX idx_market_calendar_exchange_date ON market_calendars (exchange, holiday_date);
