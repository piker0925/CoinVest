-- V11: orders 테이블에 예약 주문 및 정산 정보 컬럼 추가
ALTER TABLE orders ADD COLUMN reservation BOOLEAN DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN reservation_triggered_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN asset_class VARCHAR(20);
ALTER TABLE orders ADD COLUMN currency VARCHAR(10);

-- 기존 데이터 마이그레이션 (기본값 설정)
UPDATE orders SET reservation = FALSE WHERE reservation IS NULL;
UPDATE orders SET asset_class = 'CRYPTO' WHERE asset_class IS NULL;
UPDATE orders SET currency = 'KRW' WHERE currency IS NULL;

-- 제약 조건 추가
ALTER TABLE orders ALTER COLUMN reservation SET NOT NULL;
ALTER TABLE orders ALTER COLUMN asset_class SET NOT NULL;
ALTER TABLE orders ALTER COLUMN currency SET NOT NULL;

CREATE INDEX idx_orders_reservation_status ON orders (reservation, status) WHERE reservation = TRUE;
