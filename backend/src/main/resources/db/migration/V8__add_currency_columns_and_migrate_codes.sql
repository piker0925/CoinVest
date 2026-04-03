-- V8: 기존 거래/주문 테이블에 통화 및 범용 코드 필드 적용

-- 1. positions 테이블 변경
ALTER TABLE positions ADD COLUMN currency VARCHAR(10) DEFAULT 'KRW' NOT NULL;
ALTER TABLE positions RENAME COLUMN market_code TO universal_code;
ALTER TABLE positions DROP CONSTRAINT positions_user_id_market_code_key;
ALTER TABLE positions ADD CONSTRAINT positions_user_id_universal_code_key UNIQUE (user_id, universal_code);
-- 업비트 마켓 코드(KRW-BTC)를 범용 코드(CRYPTO:BTC)로 변환
UPDATE positions SET universal_code = CONCAT('CRYPTO:', SUBSTRING(universal_code FROM 5)) WHERE universal_code LIKE 'KRW-%';

-- 2. orders 테이블 변경
ALTER TABLE orders ADD COLUMN currency VARCHAR(10) DEFAULT 'KRW' NOT NULL;
ALTER TABLE orders ADD COLUMN asset_class VARCHAR(20) DEFAULT 'CRYPTO' NOT NULL;
ALTER TABLE orders RENAME COLUMN market_code TO universal_code;
UPDATE orders SET universal_code = CONCAT('CRYPTO:', SUBSTRING(universal_code FROM 5)) WHERE universal_code LIKE 'KRW-%';

-- 3. trades 테이블 변경
ALTER TABLE trades ADD COLUMN currency VARCHAR(10) DEFAULT 'KRW' NOT NULL;
ALTER TABLE trades ADD COLUMN exchange_rate_snapshot NUMERIC(20, 6);
ALTER TABLE trades RENAME COLUMN market_code TO universal_code;
UPDATE trades SET universal_code = CONCAT('CRYPTO:', SUBSTRING(universal_code FROM 5)) WHERE universal_code LIKE 'KRW-%';

-- 4. portfolio_assets 테이블 변경
ALTER TABLE portfolio_assets RENAME COLUMN market_code TO universal_code;
UPDATE portfolio_assets SET universal_code = CONCAT('CRYPTO:', SUBSTRING(universal_code FROM 5)) WHERE universal_code LIKE 'KRW-%';
