-- Phase 6A: 봇 전략 엔진 테이블 (StrategyType 명칭 통일 및 성능 지표 고도화)

-- 봇 설정
CREATE TABLE trading_bots (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE REFERENCES users(id),
    strategy_type  VARCHAR(30) NOT NULL,
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    price_mode     VARCHAR(10) NOT NULL DEFAULT 'DEMO',
    config         JSONB,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 봇 일일 자산 스냅샷
CREATE TABLE bot_performances (
    id                BIGSERIAL PRIMARY KEY,
    bot_id            BIGINT NOT NULL REFERENCES trading_bots(id),
    snapshot_date     TIMESTAMP NOT NULL,
    total_asset_value NUMERIC(38, 20) NOT NULL,
    daily_return_rate NUMERIC(38, 20) NOT NULL DEFAULT 0,
    net_contribution  NUMERIC(38, 20) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (bot_id, snapshot_date)
);

CREATE INDEX idx_bot_perf_bot_date ON bot_performances(bot_id, snapshot_date DESC);

-- 봇 다기간 집계 통계
CREATE TABLE bot_statistics (
    id           BIGSERIAL PRIMARY KEY,
    bot_id       BIGINT NOT NULL REFERENCES trading_bots(id),
    period       VARCHAR(10) NOT NULL,  -- '1M', '3M', 'ALL'
    return_rate  NUMERIC(38, 20),
    mdd          NUMERIC(38, 20),
    sharpe_ratio NUMERIC(38, 20),
    win_rate     NUMERIC(38, 20),
    trade_count  INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (bot_id, period)
);
