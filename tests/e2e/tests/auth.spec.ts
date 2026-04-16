import { test, expect } from '@playwright/test';
import { generateTestUser, signup, login } from '../helpers/api';

test.describe('Auth Flow', () => {
  test('should_signup_and_login_via_ui', async ({ page }) => {
    const user = generateTestUser();

    // 1. 회원가입 UI
    await page.goto('/login');
    await page.getByText('계정이 없으신가요? 회원가입').click();
    // CardTitle은 <div data-slot="card-title">으로 렌더링 — heading role 없음
    await expect(page.locator('[data-slot="card-title"]', { hasText: '회원가입' })).toBeVisible({ timeout: 10_000 });

    await page.locator('#nickname').fill(user.nickname);
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(user.password);
    await page.getByRole('button', { name: '가입하기' }).click();

    // 회원가입 성공 → 로그인 폼으로 전환
    await expect(page.locator('[data-slot="card-title"]', { hasText: '로그인' })).toBeVisible({ timeout: 10_000 });

    // 2. 로그인 UI — form 내부 submit 버튼을 명시적으로 타겟
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(user.password);
    await page.locator('form').getByRole('button', { name: '로그인' }).click();

    // 대시보드로 리다이렉트
    await page.waitForURL('**/dashboard', { timeout: 15_000 });
    await expect(page).toHaveURL(/dashboard/);
  });

  test('should_reject_duplicate_email', async ({ page, request }) => {
    const user = generateTestUser();
    await signup(request, user);

    await page.goto('/login');
    await page.getByText('계정이 없으신가요? 회원가입').click();

    await page.locator('#nickname').fill('dup_tester');
    await page.locator('#email').fill(user.email);
    await page.locator('#password').fill(user.password);
    await page.getByRole('button', { name: '가입하기' }).click();

    // 에러 토스트 노출 확인
    await expect(page.locator('[data-sonner-toast]')).toBeVisible({ timeout: 5_000 });
  });

  test('should_reject_invalid_credentials', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#email').fill('nonexistent@test.local');
    await page.locator('#password').fill('WrongPass1!');
    // form 내부 submit 버튼만 타겟 (Navbar "로그인" 버튼과 구분)
    await page.locator('form').getByRole('button', { name: '로그인' }).click();

    await expect(page.locator('[data-sonner-toast]')).toBeVisible({ timeout: 5_000 });
    await expect(page).toHaveURL(/login/);
  });

  test('should_logout_and_redirect', async ({ page, request }) => {
    const user = generateTestUser();
    await signup(request, user);
    const result = await login(request, user.email, user.password);

    await page.goto('/login');
    await page.evaluate((token) => localStorage.setItem('token', token), result.accessToken);
    await page.goto('/dashboard');
    await page.waitForURL('**/dashboard', { timeout: 10_000 });

    // 로그아웃 버튼 클릭
    await page.getByRole('button', { name: '로그아웃' }).click();
    await page.waitForURL('**/login', { timeout: 10_000 });
    await expect(page).toHaveURL(/login/);
  });
});
