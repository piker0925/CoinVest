-- 포트폴리오 일별 가치 스냅샷 테이블
-- 기간별 수익률 계산(1M, 3M, ALL)의 시계열 데이터 기반
CREATE TABLE portfolio_snapshots (
    id               BIGSERIAL PRIMARY KEY,
    portfolio_id     BIGINT       NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    snapshot_date    DATE         NOT NULL,
    total_value_base DECIMAL(20,4) NOT NULL, -- 총자산 (자산 평가액 + 현금, 기준 통화)
    net_contribution DECIMAL(20,4) NOT NULL, -- 스냅샷 시점의 순기여금 (수익률 계산 분모 보정용)
    price_mode       VARCHAR(20)  NOT NULL,  -- DEMO / LIVE 격리
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_portfolio_snapshot_date UNIQUE (portfolio_id, snapshot_date)
);

CREATE INDEX idx_portfolio_snapshots_portfolio_date
    ON portfolio_snapshots (portfolio_id, snapshot_date DESC);
