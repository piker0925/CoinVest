-- ==========================================
-- CoinVest 통합 초기 스키마 및 마스터 데이터 (V1 Baselining Finalized)
-- 작성일: 2026-04-11
-- 구성: Phase 1 ~ Phase 8 통합 + 운영 최적화 및 보안 보강
-- ==========================================

-- ---------------------------------------------------------
-- [FUNCTION] updated_at 자동 갱신 트리거 함수 정의
-- ---------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ---------------------------------------------------------
-- [SCHEMA] 테이블 정의
-- ---------------------------------------------------------

-- 1. 사용자 및 기초 인프라
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(100) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'USER',
    auth_provider VARCHAR(50)  NOT NULL DEFAULT 'LOCAL',
    provider_id   VARCHAR(255),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE market_calendars (
    id           BIGSERIAL PRIMARY KEY,
    exchange     VARCHAR(20)  NOT NULL,
    holiday_date DATE         NOT NULL,
    description  VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_market_calendar_date UNIQUE (exchange, holiday_date)
);
CREATE INDEX idx_market_calendar_exchange_date ON market_calendars (exchange, holiday_date);

-- 2. 자산 및 시세 마스터 (가상화 명칭 사용)
CREATE TABLE coin_markets (
    id            BIGSERIAL PRIMARY KEY,
    market_code   VARCHAR(20)  NOT NULL UNIQUE,
    korean_name   VARCHAR(100) NOT NULL,
    english_name  VARCHAR(100),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE assets (
    id             BIGSERIAL PRIMARY KEY,
    universal_code VARCHAR(50)  NOT NULL UNIQUE,
    external_code  VARCHAR(50)  NOT NULL,
    exchange_code  VARCHAR(20),
    asset_class    VARCHAR(20)  NOT NULL,
    quote_currency VARCHAR(10)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    fee_rate       NUMERIC(10, 6) NOT NULL,
    is_demo        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_assets_is_demo ON assets(is_demo);

CREATE TABLE exchange_rates (
    id             BIGSERIAL PRIMARY KEY,
    base_currency  VARCHAR(10) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    rate           NUMERIC(20, 6) NOT NULL,
    snapshot_date  TIMESTAMP NOT NULL, -- DATE에서 TIMESTAMP로 상향 (데이터 일관성)
    fetched_at     TIMESTAMP NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. 계좌 및 잔고
CREATE TABLE virtual_accounts (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    account_number         VARCHAR(50)  NOT NULL UNIQUE,
    total_net_contribution NUMERIC(38, 20) NOT NULL DEFAULT 0,
    version                BIGINT       NOT NULL DEFAULT 0,
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE balances (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT      NOT NULL REFERENCES virtual_accounts(id) ON DELETE CASCADE,
    currency    VARCHAR(10) NOT NULL,
    available   NUMERIC(38, 20) NOT NULL DEFAULT 0,
    locked      NUMERIC(38, 20) NOT NULL DEFAULT 0,
    unsettled   NUMERIC(38, 20) NOT NULL DEFAULT 0,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_balances_account_currency UNIQUE (account_id, currency)
);

-- 4. 포트폴리오 및 성과 스냅샷
CREATE TABLE portfolios (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users(id),
    name               VARCHAR(255) NOT NULL,
    initial_investment NUMERIC(38, 20) NOT NULL,
    net_contribution   NUMERIC(38, 20) NOT NULL DEFAULT 0,
    base_currency      VARCHAR(10)  NOT NULL DEFAULT 'KRW',
    price_mode         VARCHAR(20)  NOT NULL DEFAULT 'DEMO',
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_portfolios_price_mode ON portfolios(price_mode);

CREATE TABLE portfolio_assets (
    id             BIGSERIAL PRIMARY KEY,
    portfolio_id   BIGINT       NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    universal_code VARCHAR(50)  NOT NULL,
    currency       VARCHAR(10)  NOT NULL DEFAULT 'KRW',
    target_weight  NUMERIC(10, 6) NOT NULL,
    quantity       NUMERIC(38, 20) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE portfolio_snapshots (
    id                     BIGSERIAL PRIMARY KEY,
    portfolio_id           BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    snapshot_date          TIMESTAMP NOT NULL,
    total_evaluation_base  NUMERIC(38, 20) NOT NULL,
    net_contribution       NUMERIC(38, 20) NOT NULL,
    price_mode             VARCHAR(20)  NOT NULL DEFAULT 'DEMO',
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (portfolio_id, snapshot_date)
);

-- 5. 거래 및 주문 엔진
CREATE TABLE orders (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT      NOT NULL REFERENCES users(id),
    universal_code            VARCHAR(50) NOT NULL,
    currency                  VARCHAR(10) NOT NULL DEFAULT 'KRW',
    asset_class               VARCHAR(20) NOT NULL,
    side                      VARCHAR(10) NOT NULL,
    order_type                VARCHAR(10) NOT NULL,
    price                     NUMERIC(38, 20),
    quantity                  NUMERIC(38, 20) NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    price_mode                VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    reservation               BOOLEAN     NOT NULL DEFAULT FALSE,
    reservation_triggered_at  TIMESTAMP,
    version                   BIGINT      NOT NULL DEFAULT 0,
    filled_at                 TIMESTAMP,
    created_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_orders_price_mode ON orders(price_mode);
CREATE INDEX idx_orders_reservation_status ON orders (reservation, status) WHERE reservation = TRUE;

CREATE TABLE trades (
    id                     BIGSERIAL PRIMARY KEY,
    order_id               BIGINT      NOT NULL REFERENCES orders(id),
    user_id                BIGINT      NOT NULL REFERENCES users(id),
    universal_code         VARCHAR(50) NOT NULL,
    currency               VARCHAR(10) NOT NULL DEFAULT 'KRW',
    price                  NUMERIC(38, 20) NOT NULL,
    quantity               NUMERIC(38, 20) NOT NULL,
    fee                    NUMERIC(38, 20) NOT NULL DEFAULT 0,
    realized_pnl           NUMERIC(38, 20) NOT NULL DEFAULT 0,
    exchange_rate_snapshot NUMERIC(20, 6) NOT NULL DEFAULT 1.0,
    settlement_date        DATE        NOT NULL,
    price_mode             VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    created_at             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_trades_price_mode ON trades(price_mode);

CREATE TABLE positions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users(id),
    universal_code VARCHAR(50) NOT NULL,
    currency       VARCHAR(10) NOT NULL DEFAULT 'KRW',
    avg_buy_price  NUMERIC(38, 20) NOT NULL DEFAULT 0,
    quantity       NUMERIC(38, 20) NOT NULL DEFAULT 0,
    locked_quantity NUMERIC(38, 20) NOT NULL DEFAULT 0,
    realized_pnl   NUMERIC(38, 20) NOT NULL DEFAULT 0,
    price_mode     VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_positions_user_asset_mode UNIQUE (user_id, universal_code, price_mode)
);
CREATE INDEX idx_positions_price_mode ON positions(price_mode);

CREATE TABLE settlements (
    id              BIGSERIAL PRIMARY KEY,
    trade_id        BIGINT      NOT NULL REFERENCES trades(id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency        VARCHAR(10) NOT NULL,
    amount          NUMERIC(38, 20) NOT NULL,
    settlement_date DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    price_mode      VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_settlements_date_status ON settlements (settlement_date, status);
CREATE INDEX idx_settlements_price_mode ON settlements(price_mode);

-- 6. 알림 및 자동화 시스템
CREATE TABLE alert_settings (
    id                 BIGSERIAL PRIMARY KEY,
    portfolio_id       BIGINT  NOT NULL UNIQUE REFERENCES portfolios(id) ON DELETE CASCADE,
    discord_webhook_url TEXT,
    deviation_threshold NUMERIC(38, 20) NOT NULL DEFAULT 0.0500, -- 정밀도 상향 (전역 컨벤션 통일)
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE alert_histories (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES users(id) ON DELETE CASCADE,
    portfolio_id BIGINT REFERENCES portfolios(id) ON DELETE CASCADE,
    message      TEXT        NOT NULL,
    type         VARCHAR(30) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_alert_histories_portfolio_id ON alert_histories(portfolio_id);
CREATE INDEX idx_alert_histories_user_id ON alert_histories(user_id);

CREATE TABLE stop_loss_orders (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position_id   BIGINT      NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    trigger_price NUMERIC(38, 20) NOT NULL,
    quantity      NUMERIC(38, 20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_slo_position_status ON stop_loss_orders (position_id, status);

CREATE TABLE take_profit_orders (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position_id   BIGINT      NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    trigger_price NUMERIC(38, 20) NOT NULL,
    quantity      NUMERIC(38, 20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tpo_position_status ON take_profit_orders (position_id, status);

-- 7. 봇 전략 및 성과 통계
CREATE TABLE trading_bots (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL UNIQUE REFERENCES users(id),
    strategy_type  VARCHAR(30) NOT NULL,
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    price_mode     VARCHAR(10) NOT NULL DEFAULT 'DEMO',
    config         JSONB,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bot_performances (
    id                BIGSERIAL PRIMARY KEY,
    bot_id            BIGINT      NOT NULL REFERENCES trading_bots(id) ON DELETE CASCADE,
    snapshot_date     TIMESTAMP   NOT NULL,
    total_asset_value NUMERIC(38, 20) NOT NULL,
    daily_return_rate NUMERIC(38, 20) NOT NULL DEFAULT 0,
    net_contribution  NUMERIC(38, 20) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bot_id, snapshot_date)
);
CREATE INDEX idx_bot_perf_bot_date ON bot_performances(bot_id, snapshot_date DESC);

CREATE TABLE bot_statistics (
    id           BIGSERIAL PRIMARY KEY,
    bot_id       BIGINT      NOT NULL REFERENCES trading_bots(id) ON DELETE CASCADE,
    period       VARCHAR(10) NOT NULL,
    return_rate  NUMERIC(38, 20),
    mdd          NUMERIC(38, 20),
    sharpe_ratio NUMERIC(38, 20),
    win_rate     NUMERIC(38, 20),
    trade_count  INTEGER     NOT NULL DEFAULT 0,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bot_id, period)
);

-- ---------------------------------------------------------
-- [TRIGGER] updated_at 자동 갱신 적용
-- ---------------------------------------------------------
CREATE TRIGGER trg_update_users BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_market_calendars BEFORE UPDATE ON market_calendars FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_coin_markets BEFORE UPDATE ON coin_markets FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_assets BEFORE UPDATE ON assets FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_exchange_rates BEFORE UPDATE ON exchange_rates FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_virtual_accounts BEFORE UPDATE ON virtual_accounts FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_balances BEFORE UPDATE ON balances FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_portfolios BEFORE UPDATE ON portfolios FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_portfolio_assets BEFORE UPDATE ON portfolio_assets FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_orders BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_trades BEFORE UPDATE ON trades FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_positions BEFORE UPDATE ON positions FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_settlements BEFORE UPDATE ON settlements FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_alert_settings BEFORE UPDATE ON alert_settings FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_trading_bots BEFORE UPDATE ON trading_bots FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_bot_performances BEFORE UPDATE ON bot_performances FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_bot_statistics BEFORE UPDATE ON bot_statistics FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_stop_loss_orders BEFORE UPDATE ON stop_loss_orders FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();
CREATE TRIGGER trg_update_take_profit_orders BEFORE UPDATE ON take_profit_orders FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at_column();

-- ---------------------------------------------------------
-- [MASTER SEED DATA] 운영 필수 마스터 정보
-- ---------------------------------------------------------

-- 1. 마켓 및 자산 마스터 (가상화)
INSERT INTO coin_markets (market_code, korean_name, english_name, is_active)
VALUES 
('V-ALPHA', '알파 쿼크', 'Alpha Quark', TRUE),
('V-BETA', '베타 레이', 'Beta Ray', TRUE)
ON CONFLICT (market_code) DO NOTHING;

INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES 
('VIRTUAL:ALPHA', 'V-ALPHA', 'Alpha Quark', 'VIRTUAL', 'KRW', 0.0005, FALSE),
('VIRTUAL:BETA', 'V-BETA', 'Beta Ray', 'VIRTUAL', 'KRW', 0.0005, FALSE)
ON CONFLICT DO NOTHING;

-- 2. 2026 거래소 공휴일 (오늘 이후)
INSERT INTO market_calendars (exchange, holiday_date, description) VALUES 
('KRX', '2026-05-01', 'Seasonal A'), ('NYSE', '2026-05-25', 'Bank Holiday')
ON CONFLICT (exchange, holiday_date) DO NOTHING;

-- 3. 시스템 전략 봇 전용 계정 및 봇 생성
-- MOMENTUM 봇
INSERT INTO users (email, password_hash, nickname, role, is_active)
VALUES ('momentum-bot@coinvest.com', 'N/A_SYSTEM_ACCOUNT_DISABLED', 'MomentumBot', 'ADMIN', TRUE) ON CONFLICT DO NOTHING;

INSERT INTO trading_bots (user_id, strategy_type, status, price_mode, config)
SELECT id, 'MOMENTUM', 'ACTIVE', 'DEMO', '{"targetAssets": ["VIRTUAL:ALPHA"]}' FROM users WHERE email = 'momentum-bot@coinvest.com'
ON CONFLICT (user_id) DO NOTHING;

-- MEAN_REVERSION 봇
INSERT INTO users (email, password_hash, nickname, role, is_active)
VALUES ('reversion-bot@coinvest.com', 'N/A_SYSTEM_ACCOUNT_DISABLED', 'ReversionBot', 'ADMIN', TRUE) ON CONFLICT DO NOTHING;

INSERT INTO trading_bots (user_id, strategy_type, status, price_mode, config)
SELECT id, 'MEAN_REVERSION', 'ACTIVE', 'DEMO', '{"targetAssets": ["VIRTUAL:BETA"]}' FROM users WHERE email = 'reversion-bot@coinvest.com'
ON CONFLICT (user_id) DO NOTHING;

-- RANDOM_BASELINE 봇
INSERT INTO users (email, password_hash, nickname, role, is_active)
VALUES ('random-bot@coinvest.com', 'N/A_SYSTEM_ACCOUNT_DISABLED', 'RandomBot', 'ADMIN', TRUE) ON CONFLICT DO NOTHING;

INSERT INTO trading_bots (user_id, strategy_type, status, price_mode, config)
SELECT id, 'RANDOM_BASELINE', 'ACTIVE', 'DEMO', '{"targetAssets": ["VIRTUAL:ALPHA", "VIRTUAL:BETA"]}' FROM users WHERE email = 'random-bot@coinvest.com'
ON CONFLICT (user_id) DO NOTHING;
