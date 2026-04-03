-- V7: 기존 virtual_accounts의 데이터를 balances로 이관 (KRW)

INSERT INTO balances (account_id, currency, available, locked, unsettled, version, created_at, updated_at)
SELECT 
    id, 
    'KRW', 
    balance_krw - locked_krw, 
    locked_krw, 
    0.0000, 
    0, 
    created_at, 
    updated_at
FROM virtual_accounts;
