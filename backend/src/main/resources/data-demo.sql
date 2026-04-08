-- Demo 모드 전용 패러디 자산 시드 데이터 (15개)
-- CRYPTO (5개)
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('CRYPTO:VTC', 'VTC', 'VirtualCoin', 'CRYPTO', 'KRW', 0.0005, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('CRYPTO:NEON', 'NEON', 'NeonFlow', 'CRYPTO', 'KRW', 0.0005, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('CRYPTO:ATOM', 'ATOM', 'AtomCraft', 'CRYPTO', 'KRW', 0.0005, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('CRYPTO:ZEN', 'ZEN', 'ZenithChain', 'CRYPTO', 'KRW', 0.0005, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('CRYPTO:LUNA', 'LUNA', 'LunarDust', 'CRYPTO', 'KRW', 0.0005, TRUE) ON CONFLICT DO NOTHING;

-- US_STOCK (5개)
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('US_STOCK:PINE', 'PINE', 'Pineapple Corp', 'US_STOCK', 'USD', 0.001, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('US_STOCK:TCHN', 'TCHN', 'Tachyon Motors', 'US_STOCK', 'USD', 0.001, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('US_STOCK:PHNX', 'PHNX', 'Phoenix Tech', 'US_STOCK', 'USD', 0.001, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('US_STOCK:NOVA', 'NOVA', 'Nova Systems', 'US_STOCK', 'USD', 0.001, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('US_STOCK:ORBN', 'ORBN', 'OrbitalTech', 'US_STOCK', 'USD', 0.001, TRUE) ON CONFLICT DO NOTHING;

-- KR_STOCK (5개)
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('KR_STOCK:SSEN', 'SSEN', 'SS Electronics', 'KR_STOCK', 'KRW', 0.00015, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('KR_STOCK:LUNE', 'LUNE', 'Lune-Line', 'KR_STOCK', 'KRW', 0.00015, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('KR_STOCK:KKAO', 'KKAO', 'Kkao-Kkao', 'KR_STOCK', 'KRW', 0.00015, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('KR_STOCK:HYUN', 'HYUN', 'Hyun-Dae', 'KR_STOCK', 'KRW', 0.00015, TRUE) ON CONFLICT DO NOTHING;
INSERT INTO assets (universal_code, external_code, name, asset_class, quote_currency, fee_rate, is_demo)
VALUES ('KR_STOCK:SKHY', 'SKHY', 'SK-Hyny', 'KR_STOCK', 'KRW', 0.00015, TRUE) ON CONFLICT DO NOTHING;
