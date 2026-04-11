-- 기초 마켓 데이터 시드 (운영 필수)
-- 멱등성 보장을 위해 ON CONFLICT DO NOTHING 적용

-- Upbit 주요 코인 마켓
INSERT INTO coin_markets (market_code, korean_name, english_name, is_active, created_at, updated_at)
VALUES ('KRW-BTC', '비트코인', 'Bitcoin', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (market_code) DO NOTHING;
INSERT INTO coin_markets (market_code, korean_name, english_name, is_active, created_at, updated_at)
VALUES ('KRW-ETH', '이더리움', 'Ethereum', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (market_code) DO NOTHING;
INSERT INTO coin_markets (market_code, korean_name, english_name, is_active, created_at, updated_at)
VALUES ('KRW-XRP', '리플', 'Ripple', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (market_code) DO NOTHING;
INSERT INTO coin_markets (market_code, korean_name, english_name, is_active, created_at, updated_at)
VALUES ('KRW-SOL', '솔라나', 'Solana', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (market_code) DO NOTHING;

-- 시스템 내부 운영을 위한 핵심 자산 정의 (실제 API 연동 대상)
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo, created_at, updated_at)
VALUES ('CRYPTO:BTC', 'KRW-BTC', 'Bitcoin', 'CRYPTO', 'KRW', 0.0005, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (universal_code) DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo, created_at, updated_at)
VALUES ('CRYPTO:ETH', 'KRW-ETH', 'Ethereum', 'CRYPTO', 'KRW', 0.0005, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (universal_code) DO NOTHING;
