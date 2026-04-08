-- assets 테이블에 가상 자산 여부 컬럼 추가
ALTER TABLE assets ADD COLUMN is_demo BOOLEAN DEFAULT FALSE;

-- 인덱스 추가 (조회 성능 최적화)
CREATE INDEX idx_assets_is_demo ON assets(is_demo);
