'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuthStore } from '@/store/useAuthStore';
import { 
  LayoutDashboard, 
  CandlestickChart, 
  Briefcase, 
  Cpu, 
  History,
  ShieldCheck
} from 'lucide-react';

const baseMenuItems = [
  { name: '대시보드', href: '/dashboard', icon: LayoutDashboard },
  { name: '거래소', href: '/trading', icon: CandlestickChart },
  { name: '포트폴리오', href: '/portfolio', icon: Briefcase },
  { name: '봇 관리', href: '/bots', icon: Cpu },
  { name: '히스토리', href: '/history', icon: History },
];

export const Sidebar = () => {
  const pathname = usePathname();
  const { user } = useAuthStore();

  const isAdmin = user?.role === 'ROLE_ADMIN';

  return (
    <aside className="w-64 border-r border-slate-800 bg-slate-950 flex flex-col h-[calc(100vh-64px)]">
      <nav className="flex-1 p-4 space-y-1">
        {baseMenuItems.map((item) => {
          const isActive = pathname.startsWith(item.href);
          const Icon = item.icon;
          
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                isActive 
                  ? 'bg-slate-800 text-white shadow-sm' 
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <Icon size={18} />
              {item.name}
            </Link>
          );
        })}

        {/* ROLE_ADMIN 전용 관리자 메뉴 노출 */}
        {isAdmin && (
          <>
            <div className="mt-6 mb-2 px-4">
              <p className="text-[10px] font-bold text-slate-600 uppercase tracking-widest">Admin Only</p>
            </div>
            <Link
              href="/admin"
              className={`flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                pathname.startsWith('/admin')
                  ? 'bg-red-900/20 text-red-500 border border-red-900/30' 
                  : 'text-red-400/70 hover:bg-red-900/10 hover:text-red-400'
              }`}
            >
              <ShieldCheck size={18} />
              시스템 모니터링
            </Link>
          </>
        )}
      </nav>
      
      <div className="p-4 border-t border-slate-800">
        <div className="bg-slate-900/50 p-3 rounded-lg border border-slate-800/50">
          <p className="text-[10px] text-slate-500 leading-relaxed uppercase tracking-wider font-bold">
            Disclaimer
          </p>
          <p className="text-[10px] text-slate-600 mt-1">
            본 서비스는 교육용 모의투자 시뮬레이션으로 실제 금융 자산의 가치를 보증하지 않습니다.
          </p>
        </div>
      </div>
    </aside>
  );
};
