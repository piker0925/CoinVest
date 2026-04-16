import { APIRequestContext, request as playwrightRequest } from '@playwright/test';

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';
const API_PREFIX = `${BACKEND_URL}/api/v1`;

export interface TestUser {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginResult {
  accessToken: string;
  email: string;
  role: string;
}

/**
 * E2E 테스트용 고유 사용자를 생성한다.
 * 타임스탬프 + 랜덤 접미어로 이메일 충돌을 방지한다.
 */
export function generateTestUser(): TestUser {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
  return {
    email: `e2e_${suffix}@test.local`,
    password: 'Test1234!@',
    nickname: `tester_${suffix.slice(-6)}`,
  };
}

export async function signup(request: APIRequestContext, user: TestUser): Promise<void> {
  const res = await request.post(`${API_PREFIX}/auth/signup`, {
    data: user,
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok()) {
    throw new Error(`Signup failed (${res.status()}): ${await res.text()}`);
  }
}

export async function login(request: APIRequestContext, email: string, password: string): Promise<LoginResult> {
  const res = await request.post(`${API_PREFIX}/auth/login`, {
    data: { email, password },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok()) {
    throw new Error(`Login failed (${res.status()}): ${await res.text()}`);
  }
  const body = await res.json();
  return body.data;
}

export async function logout(request: APIRequestContext, accessToken: string): Promise<void> {
  await request.post(`${API_PREFIX}/auth/logout`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}
