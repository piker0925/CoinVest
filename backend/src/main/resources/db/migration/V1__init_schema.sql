-- Phase 1 초기 스키마
-- 2주차부터 추가 테이블 생성 (User, Auth, Portfolio)

-- users 테이블 (Phase 1: 인증 및 회원가입)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, -- 해싱된 비밀번호
    nickname VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER', -- USER, ADMIN
    auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL', -- LOCAL, KAKAO
    provider_id VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE, -- soft-delete 대용
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- coin_markets 테이블 (4주차: Upbit 마켓 목록)
CREATE TABLE IF NOT EXISTS coin_markets (
    id BIGSERIAL PRIMARY KEY,
    market_code VARCHAR(20) NOT NULL UNIQUE, -- KRW-BTC, KRW-ETH 등
    korean_name VARCHAR(100) NOT NULL,
    english_name VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_auth_provider_id ON users(auth_provider, provider_id);
CREATE INDEX idx_coin_markets_code ON coin_markets(market_code);
CREATE INDEX idx_coin_markets_active ON coin_markets(is_active);
