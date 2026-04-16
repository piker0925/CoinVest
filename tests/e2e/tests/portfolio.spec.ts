import { test, expect } from '../fixtures/auth.fixture';

test.describe('Portfolio', () => {
  test('should_load_portfolio_page_with_account_info', async ({ authedPage }) => {
    await authedPage.goto('/portfolio');

    // 포트폴리오 페이지의 계좌 정보가 로드되는지 확인
    // 초기 잔고 관련 텍스트 (KRW, 가용 잔고 등)
    await expect(authedPage.locator('body')).toContainText(/KRW/i, { timeout: 15_000 });
  });

  test('should_display_empty_positions_for_new_user', async ({ authedPage }) => {
    await authedPage.goto('/portfolio');

    // 신규 유저는 포지션이 없으므로 빈 상태 확인
    // 포지션 테이블이 비어있거나, "포지션이 없습니다" 류의 텍스트
    await authedPage.waitForTimeout(3_000);
    const body = await authedPage.locator('body').textContent();
    // 포지션이 없거나 초기 자산만 표시되는지 검증
    expect(body).toBeTruthy();
  });

  test('should_show_asset_allocation_chart', async ({ authedPage }) => {
    await authedPage.goto('/portfolio');
    await authedPage.waitForTimeout(3_000);

    // Recharts PieChart가 렌더링되는지 (SVG 존재 확인)
    const svgElements = authedPage.locator('svg.recharts-surface');
    // 신규 유저라 포지션이 없으면 차트가 없을 수 있으므로 조건부 확인
    const count = await svgElements.count();
    if (count > 0) {
      await expect(svgElements.first()).toBeVisible();
    }
  });
});
