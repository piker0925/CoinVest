-- Phase 1B: 포트폴리오 및 자산 테이블 추가

-- portfolios 테이블
CREATE TABLE IF NOT EXISTS portfolios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    initial_investment_krw NUMERIC(20, 4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0, -- 낙관적 락 필드
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- portfolio_assets 테이블
CREATE TABLE IF NOT EXISTS portfolio_assets (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    market_code VARCHAR(20) NOT NULL,
    target_weight NUMERIC(5, 4) NOT NULL, -- 0.0000 ~ 1.0000 (0% ~ 100%)
    quantity NUMERIC(30, 18) NOT NULL, -- 보유 수량 (업비트 최대 소수점 대응)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portfolio_assets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

-- 인덱스 추가
CREATE INDEX idx_portfolios_user_id ON portfolios(user_id);
CREATE INDEX idx_portfolio_assets_portfolio_id ON portfolio_assets(portfolio_id);
CREATE INDEX idx_portfolio_assets_market_code ON portfolio_assets(market_code);
