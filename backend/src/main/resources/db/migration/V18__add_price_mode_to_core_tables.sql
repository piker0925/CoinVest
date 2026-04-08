-- 핵심 테이블에 price_mode 컬럼 추가 및 격리
-- 기존 데이터는 ADMIN이 사용 중이므로 LIVE로 초기화

ALTER TABLE portfolios ADD COLUMN price_mode VARCHAR(20) DEFAULT 'LIVE';
ALTER TABLE orders ADD COLUMN price_mode VARCHAR(20) DEFAULT 'LIVE';
ALTER TABLE trades ADD COLUMN price_mode VARCHAR(20) DEFAULT 'LIVE';
ALTER TABLE positions ADD COLUMN price_mode VARCHAR(20) DEFAULT 'LIVE';

-- 인덱스 추가 (조회 성능 최적화)
CREATE INDEX idx_portfolios_price_mode ON portfolios(price_mode);
CREATE INDEX idx_orders_price_mode ON orders(price_mode);
CREATE INDEX idx_trades_price_mode ON trades(price_mode);
CREATE INDEX idx_positions_price_mode ON positions(price_mode);
