import { test, expect } from '../fixtures/auth.fixture';

test.describe('Dashboard', () => {
  test('should_display_account_summary_after_login', async ({ authedPage }) => {
    await authedPage.goto('/dashboard');

    // 환영 메시지 확인
    await expect(authedPage.getByText('님!')).toBeVisible({ timeout: 10_000 });

    // 계좌 총액 카드가 렌더링되는지 확인
    await expect(authedPage.getByText('내 자산 총액')).toBeVisible({ timeout: 10_000 });
  });

  test('should_show_initial_balance_for_new_user', async ({ authedPage }) => {
    await authedPage.goto('/dashboard');

    // 초기 자산 1억 KRW 확인 (100,000,000 형태)
    const assetCard = authedPage.getByText('내 자산 총액').locator('..');
    await expect(assetCard).toBeVisible({ timeout: 10_000 });

    // KRW 잔고 표시 확인 (100,000,000 또는 가용 잔고 텍스트)
    await expect(authedPage.getByText('가용 잔고')).toBeVisible({ timeout: 10_000 });
  });

  test('should_navigate_to_trading_page', async ({ authedPage }) => {
    await authedPage.goto('/dashboard');

    // 트레이딩 링크/버튼 클릭
    const tradingLink = authedPage.getByRole('link', { name: /거래|trading/i });
    if (await tradingLink.count() > 0) {
      await tradingLink.first().click();
      await authedPage.waitForURL('**/trading', { timeout: 10_000 });
      await expect(authedPage).toHaveURL(/trading/);
    }
  });
});
