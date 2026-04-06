-- V12: 2026년 주요 거래소 휴장일 데이터 시드 (KRX, NYSE)
-- KRX 2026
INSERT INTO market_calendars (exchange, holiday_date, description, created_at, updated_at) VALUES 
('KRX', '2026-01-01', '신정', NOW(), NOW()),
('KRX', '2026-02-16', '설날 연휴', NOW(), NOW()),
('KRX', '2026-02-17', '설날', NOW(), NOW()),
('KRX', '2026-02-18', '설날 연휴', NOW(), NOW()),
('KRX', '2026-03-01', '삼일절', NOW(), NOW()),
('KRX', '2026-03-02', '삼일절(대체휴무)', NOW(), NOW()),
('KRX', '2026-05-01', '근로자의 날', NOW(), NOW()),
('KRX', '2026-05-05', '어린이날', NOW(), NOW()),
('KRX', '2026-05-24', '부처님 오신 날', NOW(), NOW()),
('KRX', '2026-05-25', '부처님 오신 날(대체휴무)', NOW(), NOW()),
('KRX', '2026-06-06', '현충일', NOW(), NOW()),
('KRX', '2026-08-15', '광복절', NOW(), NOW()),
('KRX', '2026-08-17', '광복절(대체휴무)', NOW(), NOW()),
('KRX', '2026-09-24', '추석 연휴', NOW(), NOW()),
('KRX', '2026-09-25', '추석', NOW(), NOW()),
('KRX', '2026-09-26', '추석 연휴', NOW(), NOW()),
('KRX', '2026-10-03', '개천절', NOW(), NOW()),
('KRX', '2026-10-05', '개천절(대체휴무)', NOW(), NOW()),
('KRX', '2026-10-09', '한글날', NOW(), NOW()),
('KRX', '2026-12-25', '성탄절', NOW(), NOW()),
('KRX', '2026-12-31', '연말 휴장일', NOW(), NOW());

-- NYSE 2026
INSERT INTO market_calendars (exchange, holiday_date, description, created_at, updated_at) VALUES 
('NYSE', '2026-01-01', 'New Year''s Day', NOW(), NOW()),
('NYSE', '2026-01-19', 'Martin Luther King, Jr. Day', NOW(), NOW()),
('NYSE', '2026-02-16', 'Presidents'' Day', NOW(), NOW()),
('NYSE', '2026-04-03', 'Good Friday', NOW(), NOW()),
('NYSE', '2026-05-25', 'Memorial Day', NOW(), NOW()),
('NYSE', '2026-06-19', 'Juneteenth', NOW(), NOW()),
('NYSE', '2026-07-03', 'Independence Day (Observed)', NOW(), NOW()),
('NYSE', '2026-09-07', 'Labor Day', NOW(), NOW()),
('NYSE', '2026-11-26', 'Thanksgiving Day', NOW(), NOW()),
('NYSE', '2026-12-25', 'Christmas Day', NOW(), NOW());
