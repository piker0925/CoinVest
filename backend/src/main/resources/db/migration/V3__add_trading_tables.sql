-- Phase 3: 가상 매매 테이블 추가

-- virtual_accounts 테이블
CREATE TABLE IF NOT EXISTS virtual_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance_krw NUMERIC(20, 4) NOT NULL DEFAULT 10000000.0000, -- 초기 자금 1,000만
    locked_krw NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_virtual_accounts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- orders 테이블
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    market_code VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL, -- BUY, SELL
    order_type VARCHAR(10) NOT NULL, -- MARKET, LIMIT
    price NUMERIC(20, 4), -- 시장가 주문시 NULL 가능
    quantity NUMERIC(30, 18) NOT NULL,
    status VARCHAR(20) NOT NULL, -- PENDING, FILLED, CANCELLED, EXPIRED
    filled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- positions 테이블
CREATE TABLE IF NOT EXISTS positions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    market_code VARCHAR(20) NOT NULL,
    avg_buy_price NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    quantity NUMERIC(30, 18) NOT NULL DEFAULT 0.0000,
    realized_pnl NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_positions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_positions_user_market UNIQUE (user_id, market_code)
);

-- 인덱스 추가
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_positions_user_id ON positions(user_id);
