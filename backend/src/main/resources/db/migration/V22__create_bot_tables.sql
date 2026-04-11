-- Phase 6A: 봇 전략 엔진 테이블 (수정: StrategyType 명칭 통일 및 성능 지표 고도화)

-- 봇 설정
CREATE TABLE IF NOT EXISTS trading_bots (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE REFERENCES users(id),
    strategy_type  VARCHAR(30) NOT NULL, -- signal_type -> strategy_type
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    price_mode     VARCHAR(10) NOT NULL DEFAULT 'DEMO',
    config         JSONB,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 봇 일일 자산 스냅샷 (다기간 통계 계산 원본)
CREATE TABLE IF NOT EXISTS bot_performances (
    id                BIGSERIAL PRIMARY KEY,
    bot_id            BIGINT NOT NULL REFERENCES trading_bots(id),
    snapshot_date     DATE NOT NULL,
    total_asset_value NUMERIC(38, 20) NOT NULL, -- 정밀도 향상
    daily_return_rate NUMERIC(38, 20) NOT NULL DEFAULT 0,
    net_contribution  NUMERIC(38, 20) NOT NULL DEFAULT 0, -- 순기여분 추가
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (bot_id, snapshot_date)
);

CREATE INDEX IF NOT EXISTS idx_bot_perf_bot_date ON bot_performances(bot_id, snapshot_date DESC);

-- 봇 다기간 집계 통계 (배치 갱신, API 조회용)
CREATE TABLE IF NOT EXISTS bot_statistics (
    id           BIGSERIAL PRIMARY KEY,
    bot_id       BIGINT NOT NULL REFERENCES trading_bots(id),
    period       VARCHAR(10) NOT NULL,  -- '1M', '3M', 'ALL'
    return_rate  NUMERIC(38, 20),
    mdd          NUMERIC(38, 20),
    sharpe_ratio NUMERIC(38, 20), -- 샤프지수 추가
    win_rate     NUMERIC(38, 20),
    trade_count  INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (bot_id, period)
);
