-- V6: assets, exchange_rates, balances 테이블 생성

CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    universal_code VARCHAR(50) NOT NULL UNIQUE,
    external_code VARCHAR(50) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    fee_rate NUMERIC(10, 6) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE exchange_rates (
    id BIGSERIAL PRIMARY KEY,
    base_currency VARCHAR(10) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    rate NUMERIC(20, 6) NOT NULL,
    snapshot_date DATE NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE balances (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    available NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    locked NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    unsettled NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_balances_virtual_account FOREIGN KEY (account_id) REFERENCES virtual_accounts (id) ON DELETE CASCADE,
    CONSTRAINT uk_balances_account_currency UNIQUE (account_id, currency),
    CONSTRAINT chk_balances_available CHECK (available >= 0),
    CONSTRAINT chk_balances_locked CHECK (locked >= 0),
    CONSTRAINT chk_balances_unsettled CHECK (unsettled >= 0)
);
