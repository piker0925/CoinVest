-- Phase 3: 지정가/동시성 제어를 위한 컬럼 및 체결(Trade) 테이블 추가

-- 낙관적 락(Optimistic Lock)을 위한 version 컬럼 추가
ALTER TABLE virtual_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE positions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 지정가 매도를 위한 잠금 수량 컬럼 추가
ALTER TABLE positions ADD COLUMN locked_quantity NUMERIC(30, 18) NOT NULL DEFAULT 0.0000;

-- trades 테이블 (체결 내역)
CREATE TABLE IF NOT EXISTS trades (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    market_code VARCHAR(20) NOT NULL,
    price NUMERIC(20, 4) NOT NULL,
    quantity NUMERIC(30, 18) NOT NULL,
    fee NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    realized_pnl NUMERIC(20, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trades_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_trades_user_id ON trades(user_id);
CREATE INDEX idx_trades_order_id ON trades(order_id);
