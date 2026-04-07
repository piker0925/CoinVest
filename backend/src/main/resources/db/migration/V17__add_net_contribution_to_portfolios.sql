-- V17: Add net_contribution to portfolios for accurate ROI calculation
ALTER TABLE portfolios ADD COLUMN net_contribution NUMERIC(20, 4) DEFAULT 0 NOT NULL;

-- 기존 데이터 마이그레이션: 초기 투자 금액을 순 기여분으로 설정
UPDATE portfolios SET net_contribution = initial_investment;
