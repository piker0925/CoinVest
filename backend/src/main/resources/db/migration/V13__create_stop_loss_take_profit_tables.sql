-- V13: stop_loss_orders, take_profit_orders 테이블 생성
CREATE TABLE stop_loss_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    trigger_price NUMERIC(20, 4) NOT NULL,
    quantity NUMERIC(30, 18) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_slo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_slo_position FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE CASCADE
);

CREATE INDEX idx_slo_position_status ON stop_loss_orders (position_id, status);

CREATE TABLE take_profit_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    trigger_price NUMERIC(20, 4) NOT NULL,
    quantity NUMERIC(30, 18) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tpo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tpo_position FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE CASCADE
);

CREATE INDEX idx_tpo_position_status ON take_profit_orders (position_id, status);
