-- V15: Update portfolio tables for multi-currency support
ALTER TABLE portfolios RENAME COLUMN initial_investment_krw TO initial_investment;
ALTER TABLE portfolios ADD COLUMN base_currency VARCHAR(10) DEFAULT 'KRW' NOT NULL;
ALTER TABLE portfolio_assets ADD COLUMN currency VARCHAR(10) DEFAULT 'KRW' NOT NULL;

-- 기존 데이터 정합성을 위해 Asset 테이블의 quote_currency로 업데이트 (추후 배치나 로직으로 보완 가능하나 기본값 KRW로 시작)
UPDATE portfolio_assets pa
SET currency = (SELECT quote_currency FROM assets a WHERE a.universal_code = pa.universal_code)
WHERE EXISTS (SELECT 1 FROM assets a WHERE a.universal_code = pa.universal_code);
