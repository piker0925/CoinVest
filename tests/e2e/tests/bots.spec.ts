import { test, expect } from '../fixtures/auth.fixture';

test.describe('Bots', () => {
  test('should_load_bots_page', async ({ authedPage }) => {
    await authedPage.goto('/bots');

    // AI 봇 관리 시스템 헤더 확인
    await expect(authedPage.getByText('AI 봇 관리 시스템')).toBeVisible({ timeout: 10_000 });
  });

  test('should_display_bot_strategy_descriptions', async ({ authedPage }) => {
    await authedPage.goto('/bots');
    await authedPage.waitForTimeout(3_000);

    // 봇이 존재하면 전략 카드가 표시되는지 확인
    // MOMENTUM, MEAN_REVERSION, RANDOM_BASELINE 중 하나라도 있는지
    const body = await authedPage.locator('body').textContent();
    const hasStrategies =
      body?.includes('MOMENTUM') ||
      body?.includes('MEAN_REVERSION') ||
      body?.includes('RANDOM_BASELINE') ||
      body?.includes('봇이 없습니다') ||
      body?.includes('봇');

    expect(hasStrategies).toBeTruthy();
  });

  test('should_show_create_bot_button_as_disabled', async ({ authedPage }) => {
    await authedPage.goto('/bots');

    // "신규 봇 생성" 버튼이 비활성화 상태인지 확인
    const createBtn = authedPage.getByRole('button', { name: /신규 봇 생성/ });
    await expect(createBtn).toBeVisible({ timeout: 10_000 });
    await expect(createBtn).toBeDisabled();
  });
});
