-- V14: Trade 가시성 및 운영 데이터 보완
ALTER TABLE trades ADD COLUMN settlement_date DATE;

-- 기존 데이터 업데이트 (기본값)
UPDATE trades SET exchange_rate_snapshot = 1.0 WHERE exchange_rate_snapshot IS NULL;
UPDATE trades SET settlement_date = CAST(created_at AS DATE) WHERE settlement_date IS NULL;

ALTER TABLE trades ALTER COLUMN exchange_rate_snapshot SET NOT NULL;
ALTER TABLE trades ALTER COLUMN settlement_date SET NOT NULL;
