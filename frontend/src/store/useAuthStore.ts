import {create} from 'zustand';
import {authService} from '@/services/authService';

export type UserRole = 'ROLE_USER' | 'ROLE_ADMIN';
export type PriceMode = 'DEMO' | 'LIVE';

interface UserProfile {
    email: string;
    role: UserRole;
}

interface AuthState {
    user: UserProfile | null;
    mode: PriceMode;
    setAuth: (user: UserProfile) => void;
    logout: () => Promise<void>;
    hydrateFromToken: () => void;
}

/**
 * JWT payload를 라이브러리 없이 디코딩.
 * 서명 검증은 서버에서 처리하므로 payload 파싱만 수행.
 */
function decodeJwtPayload(token: string): { sub: string; role: string; exp: number } | null {
    try {
        const payload = token.split('.')[1];
        const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
        return decoded;
    } catch {
        return null;
    }
}

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    mode: 'DEMO',

    setAuth: (user) =>
        set({
            user,
            mode: user.role === 'ROLE_ADMIN' ? 'LIVE' : 'DEMO',
        }),

    logout: async () => {
        try {
            await authService.logout();
        } catch {
            // logout API 실패(401, 네트워크 오류 등)해도 로컬 상태는 반드시 정리
        } finally {
            if (typeof window !== 'undefined') {
                localStorage.removeItem('token');
            }
            set({user: null, mode: 'DEMO'});
        }
    },

    /**
     * 페이지 새로고침 시 localStorage의 accessToken을 디코딩하여 Zustand 상태 복원.
     * JWT payload에는 sub(email), role, exp가 포함됨.
     */
    hydrateFromToken: () => {
        if (typeof window === 'undefined') return;
        const token = localStorage.getItem('token');
        if (!token) return;

        const payload = decodeJwtPayload(token);
        if (!payload) {
            localStorage.removeItem('token');
            return;
        }

        // 만료된 토큰은 제거 (silent reissue는 API 호출 시 인터셉터가 처리)
        if (payload.exp * 1000 < Date.now()) {
            localStorage.removeItem('token');
            return;
        }

        const VALID_ROLES = ['USER', 'ADMIN', 'BOT'] as const;
        if (!VALID_ROLES.includes(payload.role as typeof VALID_ROLES[number])) {
            localStorage.removeItem('token');
            return;
        }

        const role = ('ROLE_' + payload.role) as UserRole;
        set({
            user: {email: payload.sub, role},
            mode: role === 'ROLE_ADMIN' ? 'LIVE' : 'DEMO',
        });
    },
}));
