import { test as base, Page } from '@playwright/test';
import { generateTestUser, signup, login, TestUser, LoginResult } from '../helpers/api';

/**
 * 인증된 상태의 Page를 제공하는 Playwright fixture.
 * 각 테스트 파일마다 고유 유저를 생성하고, localStorage에 토큰을 주입한다.
 */
type AuthFixtures = {
  authedPage: Page;
  testUser: TestUser;
  authResult: LoginResult;
};

export const test = base.extend<AuthFixtures>({
  testUser: async ({}, use) => {
    await use(generateTestUser());
  },

  authResult: async ({ request, testUser }, use) => {
    await signup(request, testUser);
    const result = await login(request, testUser.email, testUser.password);
    await use(result);
  },

  authedPage: async ({ page, authResult }, use) => {
    // localStorage에 토큰 주입을 위해 먼저 앱 도메인에 접근
    await page.goto('/login');
    await page.evaluate((token) => {
      localStorage.setItem('token', token);
    }, authResult.accessToken);
    await use(page);
  },
});

export { expect } from '@playwright/test';
