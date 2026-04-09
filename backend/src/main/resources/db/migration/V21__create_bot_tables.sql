-- V21: Trading Bot Tables
CREATE TABLE trading_bots (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    strategy_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    config JSONB,
    price_mode VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trading_bots_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE bot_performances (
    id BIGSERIAL PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    date DATE NOT NULL,
    total_assets_base DECIMAL(38, 20) NOT NULL,
    daily_return_rate DECIMAL(38, 20) NOT NULL,
    net_contribution DECIMAL(38, 20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bot_performances_bot FOREIGN KEY (bot_id) REFERENCES trading_bots(id)
);

CREATE UNIQUE INDEX idx_bot_performances_bot_date ON bot_performances(bot_id, date);

CREATE TABLE bot_statistics (
    id BIGSERIAL PRIMARY KEY,
    bot_id BIGINT NOT NULL,
    period VARCHAR(10) NOT NULL, -- 1M, 3M, ALL
    return_rate DECIMAL(38, 20) NOT NULL,
    mdd DECIMAL(38, 20) NOT NULL,
    sharpe_ratio DECIMAL(38, 20) NOT NULL,
    win_rate DECIMAL(38, 20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bot_statistics_bot FOREIGN KEY (bot_id) REFERENCES trading_bots(id)
);

CREATE UNIQUE INDEX idx_bot_statistics_bot_period ON bot_statistics(bot_id, period);
