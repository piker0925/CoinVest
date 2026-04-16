-- alert_histories의 ON DELETE CASCADE를 ON DELETE SET NULL으로 변경.
-- 이유: 사용자 또는 포트폴리오 삭제 시 감사 로그(SYSTEM_ERROR 등)까지 함께 삭제되는 문제 방지.
--      알림 이력은 운영 추적 자산이므로 참조 대상이 삭제되어도 레코드 자체는 보존해야 함.

ALTER TABLE alert_histories
    DROP CONSTRAINT IF EXISTS alert_histories_user_id_fkey,
    DROP CONSTRAINT IF EXISTS alert_histories_portfolio_id_fkey;

ALTER TABLE alert_histories
    ADD CONSTRAINT alert_histories_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT alert_histories_portfolio_id_fkey
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE SET NULL;
