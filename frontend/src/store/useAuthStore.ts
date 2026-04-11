import { create } from 'zustand';

export type UserRole = 'ROLE_USER' | 'ROLE_APPROVED' | 'ROLE_ADMIN';
export type PriceMode = 'DEMO' | 'LIVE';

interface UserProfile {
  email: string;
  role: UserRole;
}

interface AuthState {
  user: UserProfile | null;
  mode: PriceMode;
  setAuth: (user: UserProfile | null) => void;
  setMode: (mode: PriceMode) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  mode: 'DEMO',
  setAuth: (user) => set({ 
    user, 
    mode: user?.role === 'ROLE_APPROVED' || user?.role === 'ROLE_ADMIN' ? 'LIVE' : 'DEMO' 
  }),
  setMode: (mode) => set({ mode }),
  logout: () => {
    localStorage.removeItem('token');
    set({ user: null, mode: 'DEMO' });
  },
}));
