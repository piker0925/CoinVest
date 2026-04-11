-- Demo 모드 전용 가상 자산 및 테스트 계정 시드 데이터

-- 1. 가상 자산 (패러디 종목)
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('CRYPTO:VTC', 'VTC', 'VirtualCoin', 'CRYPTO', 'KRW', 0.0005, TRUE) ON CONFLICT (universal_code) DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('US_STOCK:PINE', 'PINE', 'Pineapple Corp', 'US_STOCK', 'USD', 0.001, TRUE) ON CONFLICT (universal_code) DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('KR_STOCK:SSEN', 'SSEN', 'SS Electronics', 'KR_STOCK', 'KRW', 0.00015, TRUE) ON CONFLICT (universal_code) DO NOTHING;

-- 2. 가상 테스트 계정 (비밀번호: password123!)
-- BCrypt: $2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00dmxs.TVuHOn2
INSERT INTO users (email, password_hash, nickname, role, auth_provider, is_active)
VALUES ('test@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00dmxs.TVuHOn2', '모의투자자', 'USER', 'LOCAL', TRUE)
ON CONFLICT (email) DO NOTHING;

-- 3. 가상 계좌 및 기초 잔고 생성 (테스트 유저용)
INSERT INTO virtual_accounts (user_id, account_number, total_net_contribution, is_active)
SELECT id, 'VA-TEST-001', 100000000, TRUE FROM users WHERE email = 'test@example.com'
ON CONFLICT (account_number) DO NOTHING;

-- KRW 1억 부여 (복합 키: account_id, currency)
INSERT INTO balances (account_id, currency, total, locked, available)
SELECT id, 'KRW', 100000000, 0, 100000000 FROM virtual_accounts WHERE account_number = 'VA-TEST-001'
ON CONFLICT (account_id, currency) DO NOTHING;
