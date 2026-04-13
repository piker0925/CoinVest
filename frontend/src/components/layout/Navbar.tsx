'use client';

import React from 'react';
import {useRouter} from 'next/navigation';
import {useAuthStore} from '@/store/useAuthStore';
import {authService} from '@/services/authService';
import {LogOut, ShieldAlert, ShieldCheck, User} from 'lucide-react';
import Link from 'next/link';
import {Button} from '@/components/ui/button';

export const Navbar = () => {
    const router = useRouter();
    const {user, mode, logout} = useAuthStore();

    const handleLogout = async () => {
        await authService.logout().catch(() => {
        });
        logout();
        router.push('/login');
    };

    return (
        <header
            className="h-16 border-b border-slate-800 bg-slate-950 flex items-center justify-between px-6 sticky top-0 z-50">
            <div className="flex items-center gap-4">
                <Link href="/" className="text-xl font-bold text-white tracking-tight">CoinVest</Link>
                {user && (
                    <div className={`px-3 py-1 rounded-full text-[11px] font-bold flex items-center gap-1.5 ${
                        mode === 'LIVE'
                            ? 'bg-red-500/10 text-red-500 border border-red-500/20'
                            : 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'
                    }`}>
                        {mode === 'LIVE' ? (
                            <><ShieldCheck size={14}/> 실전투자 모드</>
                        ) : (
                            <><ShieldAlert size={14}/> 모의투자 모드</>
                        )}
                    </div>
                )}
            </div>

            <div className="flex items-center gap-4">
                {user ? (
                    <div className="flex items-center gap-4">
                        <div className="flex items-center gap-2 text-slate-300">
                            <User size={18}/>
                            <span className="text-sm font-medium">{user.email}</span>
                        </div>
                        <Button
                            variant="ghost"
                            size="sm"
                            className="text-slate-400 hover:text-white hover:bg-slate-800 h-9"
                            onClick={handleLogout}
                        >
                            <LogOut size={16} className="mr-2"/>
                            로그아웃
                        </Button>
                    </div>
                ) : (
                    <Link href="/login">
                        <Button size="sm" className="font-bold h-9 px-6">로그인</Button>
                    </Link>
                )}
            </div>
        </header>
    );
};
