-- V9: settlements 테이블 생성 (T+2 정산 추적)
CREATE TABLE settlements (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    amount NUMERIC(20, 4) NOT NULL,
    settlement_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_settlements_trade FOREIGN KEY (trade_id) REFERENCES trades (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_settlements_date_status ON settlements (settlement_date, status);
