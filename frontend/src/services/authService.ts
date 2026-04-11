import apiClient from './apiClient';
import { UserRole } from '@/store/useAuthStore';

interface LoginResponse {
  token: string;
  email: string;
  role: UserRole;
}

export const authService = {
  /**
   * 실제 백엔드 로그인 API 호출
   */
  async login(email: string, password: string): Promise<LoginResponse> {
    const response = await apiClient.post('/auth/login', {
      email,
      password,
    });
    // 백엔드 ApiResponse<T> 구조에 맞춰 data 추출 (백엔드 공통 규격 확인)
    return response.data.data;
  },

  /**
   * 현재 유저 정보 가져오기 (토큰 검증용)
   */
  async getMe() {
    const response = await apiClient.get('/auth/me');
    return response.data.data;
  }
};
