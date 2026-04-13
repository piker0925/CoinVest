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

    async logout(): Promise<void> {
        const token = localStorage.getItem('token');
        if (token) {
            await apiClient.post('/auth/logout', null, {
                headers: {Authorization: `Bearer ${token}`},
            });
        }
    },
};
