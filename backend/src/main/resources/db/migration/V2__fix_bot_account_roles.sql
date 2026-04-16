-- 봇 시스템 계정의 role을 ADMIN에서 BOT으로 변경.
-- 이유: 봇은 매매 API만 호출하므로 ADMIN 권한은 최소 권한 원칙(Least Privilege) 위반.
--      BOT role은 일반 트레이딩 API 범위 내에서만 동작하고 관리자 기능에 접근 불가.
UPDATE users
SET role = 'BOT'
WHERE email IN (
    'momentum-bot@coinvest.com',
    'reversion-bot@coinvest.com',
    'random-bot@coinvest.com'
)
  AND role = 'ADMIN';
