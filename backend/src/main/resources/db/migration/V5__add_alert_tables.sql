-- 알림 설정 테이블
CREATE TABLE alert_settings (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL UNIQUE,
    discord_webhook_url TEXT,
    deviation_threshold DECIMAL(20, 4) NOT NULL DEFAULT 0.0500,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_alert_settings_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE
);

-- 알림 이력 테이블
CREATE TABLE alert_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_alert_histories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_histories_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE
);

CREATE INDEX idx_alert_histories_portfolio_id ON alert_histories(portfolio_id);
CREATE INDEX idx_alert_histories_user_id ON alert_histories(user_id);
