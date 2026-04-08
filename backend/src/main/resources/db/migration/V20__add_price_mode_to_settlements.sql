-- settlements 테이블에 price_mode 컬럼 추가
ALTER TABLE settlements ADD COLUMN price_mode VARCHAR(20) DEFAULT 'LIVE';

-- 인덱스 추가
CREATE INDEX idx_settlements_price_mode ON settlements(price_mode);
