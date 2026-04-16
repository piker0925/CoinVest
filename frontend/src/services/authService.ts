import apiClient from './apiClient';
import {UserRole} from '@/store/useAuthStore';

export interface LoginResponse {
    accessToken: string;
    email: string;
    role: UserRole;
}

export const authService = {
    async login(email: string, password: string): Promise<LoginResponse> {
        const response = await apiClient.post('/auth/login', {email, password});
        return response.data.data;
    },

    async signup(email: string, password: string, nickname: string): Promise<void> {
        await apiClient.post('/auth/signup', {email, password, nickname});
    },

    async logout(): Promise<void> {
        await apiClient.post('/auth/logout');
    },
};
