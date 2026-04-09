-- Phase 6A: 전략별 시스템 봇 초기 데이터
--
-- 봇 계정은 UserRole.BOT으로 생성되며 AuthService에서 로그인이 차단됨.
-- password_hash는 BCrypt 형식이나 실제 로그인에 사용되지 않음.
-- 초기 자금: KRW 1억 / USD 1만 (시뮬레이션 전용)

DO $$
DECLARE
    uid_momentum   BIGINT;
    uid_meanrev    BIGINT;
    uid_index      BIGINT;
    uid_random     BIGINT;
    acc_momentum   BIGINT;
    acc_meanrev    BIGINT;
    acc_index      BIGINT;
    acc_random     BIGINT;
BEGIN

-- 1. Bot 전용 User 계정 생성 (role = 'BOT', 로그인 불가)
INSERT INTO users (email, password_hash, nickname, role, auth_provider, is_active, created_at, updated_at)
VALUES ('system_bot_momentum', '$2a$10$SYSTEM_BOT_ACCOUNT_LOGIN_DISABLED_XXXXXXXXXXXXXXXXXXX', 'Momentum Bot', 'BOT', 'LOCAL', true, NOW(), NOW())
RETURNING id INTO uid_momentum;

INSERT INTO users (email, password_hash, nickname, role, auth_provider, is_active, created_at, updated_at)
VALUES ('system_bot_mean_rev', '$2a$10$SYSTEM_BOT_ACCOUNT_LOGIN_DISABLED_XXXXXXXXXXXXXXXXXXX', 'MeanReversion Bot', 'BOT', 'LOCAL', true, NOW(), NOW())
RETURNING id INTO uid_meanrev;

INSERT INTO users (email, password_hash, nickname, role, auth_provider, is_active, created_at, updated_at)
VALUES ('system_bot_index', '$2a$10$SYSTEM_BOT_ACCOUNT_LOGIN_DISABLED_XXXXXXXXXXXXXXXXXXX', 'IndexTracking Bot', 'BOT', 'LOCAL', true, NOW(), NOW())
RETURNING id INTO uid_index;

INSERT INTO users (email, password_hash, nickname, role, auth_provider, is_active, created_at, updated_at)
VALUES ('system_bot_random', '$2a$10$SYSTEM_BOT_ACCOUNT_LOGIN_DISABLED_XXXXXXXXXXXXXXXXXXX', 'Random Bot', 'BOT', 'LOCAL', true, NOW(), NOW())
RETURNING id INTO uid_random;

-- 2. VirtualAccount 생성
INSERT INTO virtual_accounts (user_id, version, created_at, updated_at) VALUES (uid_momentum, 0, NOW(), NOW()) RETURNING id INTO acc_momentum;
INSERT INTO virtual_accounts (user_id, version, created_at, updated_at) VALUES (uid_meanrev,  0, NOW(), NOW()) RETURNING id INTO acc_meanrev;
INSERT INTO virtual_accounts (user_id, version, created_at, updated_at) VALUES (uid_index,    0, NOW(), NOW()) RETURNING id INTO acc_index;
INSERT INTO virtual_accounts (user_id, version, created_at, updated_at) VALUES (uid_random,   0, NOW(), NOW()) RETURNING id INTO acc_random;

-- 3. Balance 시딩 (KRW 1억 + USD 1만)
INSERT INTO balances (account_id, currency, available, locked, unsettled, version, created_at, updated_at)
VALUES
    (acc_momentum, 'KRW', 100000000, 0, 0, 0, NOW(), NOW()),
    (acc_momentum, 'USD', 10000,     0, 0, 0, NOW(), NOW()),
    (acc_meanrev,  'KRW', 100000000, 0, 0, 0, NOW(), NOW()),
    (acc_meanrev,  'USD', 10000,     0, 0, 0, NOW(), NOW()),
    (acc_index,    'KRW', 100000000, 0, 0, 0, NOW(), NOW()),
    (acc_index,    'USD', 10000,     0, 0, 0, NOW(), NOW()),
    (acc_random,   'KRW', 100000000, 0, 0, 0, NOW(), NOW()),
    (acc_random,   'USD', 10000,     0, 0, 0, NOW(), NOW());

-- 4. TradingBot 생성
INSERT INTO trading_bots (user_id, signal_type, status, price_mode, config, created_at, updated_at)
VALUES
    (uid_momentum, 'MOMENTUM',       'ACTIVE', 'DEMO',
     '{"targetAssets": ["CRYPTO:BTC", "CRYPTO:ETH"]}',
     NOW(), NOW()),

    (uid_meanrev,  'MEAN_REVERSION', 'ACTIVE', 'DEMO',
     '{"targetAssets": ["CRYPTO:BTC", "CRYPTO:ETH", "CRYPTO:XRP"]}',
     NOW(), NOW()),

    (uid_index,    'INDEX_TRACKING', 'ACTIVE', 'DEMO',
     '{"targetAssets": ["CRYPTO:BTC", "CRYPTO:ETH", "CRYPTO:XRP"], "weights": {"CRYPTO:BTC": 0.50, "CRYPTO:ETH": 0.30, "CRYPTO:XRP": 0.20}, "rebalanceThreshold": 0.05}',
     NOW(), NOW()),

    (uid_random,   'RANDOM_BASELINE','ACTIVE', 'DEMO',
     '{"targetAssets": ["CRYPTO:BTC", "CRYPTO:ETH", "CRYPTO:XRP"]}',
     NOW(), NOW());

END $$;
