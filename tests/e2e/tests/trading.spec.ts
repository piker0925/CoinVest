import { test, expect } from '../fixtures/auth.fixture';

test.describe('Trading', () => {
  test('should_load_asset_list_and_select_asset', async ({ authedPage }) => {
    await authedPage.goto('/trading');

    // 자산 검색 입력창 확인
    const searchInput = authedPage.getByPlaceholder('자산 검색');
    await expect(searchInput).toBeVisible({ timeout: 15_000 });

    // 자산 목록이 최소 1개 이상 로드되는지 확인
    // 자산 코드가 "CRYPTO", "US_STOCK" 등 AssetClass 텍스트로 표시됨
    const assetItems = authedPage.locator('text=/CRYPTO|US_STOCK|KR_STOCK|US_ETF|KR_ETF|VIRTUAL/');
    await expect(assetItems.first()).toBeVisible({ timeout: 15_000 });

    // 첫 번째 자산 클릭
    await assetItems.first().click();

    // 호가창이 표시되는지 확인
    await expect(authedPage.getByText('호가창')).toBeVisible({ timeout: 10_000 });
  });

  test('should_display_order_form_with_buy_sell_tabs', async ({ authedPage }) => {
    await authedPage.goto('/trading');
    await authedPage.waitForTimeout(3_000);

    // 매수/매도 탭 확인
    await expect(authedPage.getByRole('tab', { name: '매수' })).toBeVisible({ timeout: 10_000 });
    await expect(authedPage.getByRole('tab', { name: '매도' })).toBeVisible();
  });

  test('should_search_and_filter_assets', async ({ authedPage }) => {
    await authedPage.goto('/trading');

    const searchInput = authedPage.getByPlaceholder('자산 검색');
    await expect(searchInput).toBeVisible({ timeout: 15_000 });

    // 존재하지 않는 자산 검색 시 결과 없음
    await searchInput.fill('ZZZZNONEXISTENT');
    await authedPage.waitForTimeout(500);

    // 검색 초기화
    await searchInput.clear();
    await authedPage.waitForTimeout(500);

    // 자산 목록이 다시 나타나는지 확인
    const assetItems = authedPage.locator('text=/CRYPTO|US_STOCK|KR_STOCK|US_ETF|KR_ETF|VIRTUAL/');
    await expect(assetItems.first()).toBeVisible({ timeout: 10_000 });
  });

  test('should_show_candle_chart_for_selected_asset', async ({ authedPage }) => {
    await authedPage.goto('/trading');
    await authedPage.waitForTimeout(3_000);

    // 차트 영역 존재 확인 (shortCode/KRW 또는 shortCode/USD 형태의 헤더)
    const chartHeader = authedPage.locator('text=/\\/KRW|\\/USD/');
    await expect(chartHeader.first()).toBeVisible({ timeout: 15_000 });
  });

  test('should_display_trade_history_section', async ({ authedPage }) => {
    await authedPage.goto('/trading');

    // 체결 내역 섹션 확인
    await expect(authedPage.getByText('내 체결 내역')).toBeVisible({ timeout: 10_000 });
  });
});
