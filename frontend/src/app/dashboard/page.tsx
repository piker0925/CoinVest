'use client';

import React from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { 
  TrendingUp, 
  TrendingDown, 
  ArrowUpRight, 
  Bot, 
  Wallet,
  ArrowRight
} from 'lucide-react';
import Link from 'next/link';

export default function DashboardPage() {
  const { user, mode } = useAuthStore();

  if (!user) return null;

  return (
    <div className="space-y-8 max-w-[1400px] mx-auto pb-10">
      <div className="flex flex-col gap-2">
        <h2 className="text-3xl font-bold tracking-tight">안녕하세요, {user.email}님!</h2>
        <p className="text-slate-500 font-medium">오늘의 투자 요약 및 시장 현황입니다.</p>
      </div>

      {/* 1. Dashboard Overview */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-bold text-slate-500 uppercase">내 자산 총액</CardTitle>
            <Wallet className="w-4 h-4 text-slate-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-mono font-bold text-white">124,500,000 <span className="text-xs">KRW</span></div>
            <p className="text-[10px] text-emerald-500 font-bold mt-1 flex items-center gap-1">
              <TrendingUp size={12} /> +1.24%
            </p>
          </CardContent>
        </Card>

        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-bold text-slate-500 uppercase">24시간 수익금</CardTitle>
            <TrendingUp className="w-4 h-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-mono font-bold text-emerald-500">+1,420,000 <span className="text-xs">KRW</span></div>
            <p className="text-[10px] text-slate-500 font-bold mt-1 uppercase tracking-widest">
              Last Updated: Now
            </p>
          </CardContent>
        </Card>

        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-bold text-slate-500 uppercase">실행 중인 봇</CardTitle>
            <Bot className="w-4 h-4 text-primary" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-mono font-bold text-white">2 <span className="text-xs text-slate-500">Active</span></div>
            <p className="text-[10px] text-slate-500 font-bold mt-1 uppercase tracking-widest">
              3 Strategies Loaded
            </p>
          </CardContent>
        </Card>

        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-xs font-bold text-slate-500 uppercase">현재 투자 모드</CardTitle>
            <div className={`w-2 h-2 rounded-full ${mode === 'LIVE' ? 'bg-red-500 animate-pulse' : 'bg-emerald-500 shadow-[0_0_10px_rgba(16,185,129,0.5)]'}`} />
          </CardHeader>
          <CardContent>
            <div className={`text-2xl font-bold ${mode === 'LIVE' ? 'text-red-500' : 'text-emerald-500'}`}>
              {mode === 'LIVE' ? 'REAL-TIME' : 'SIMULATED'}
            </div>
            <p className="text-[10px] text-slate-500 font-bold mt-1 uppercase tracking-widest">
              {user.role} Authorization
            </p>
          </CardContent>
        </Card>
      </div>

      {/* 2. Call to Action Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-slate-900/30 border border-slate-800 rounded-2xl p-8 flex flex-col justify-between hover:border-primary/50 transition-all group">
          <div className="space-y-4">
            <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center group-hover:bg-primary/20 transition-colors">
              <TrendingUp className="text-primary" />
            </div>
            <h3 className="text-2xl font-bold">지금 바로 거래를 시작하세요</h3>
            <p className="text-slate-400 font-medium leading-relaxed">
              업비트 실시간 시세와 KIS 글로벌 데이터를 기반으로<br />
              최적의 매수/매도 타이밍을 잡아보세요.
            </p>
          </div>
          <Link href="/trading">
            <Button className="mt-8 font-bold gap-2 h-12 px-6 shadow-lg shadow-primary/20">
              거래소로 이동 <ArrowRight size={18} />
            </Button>
          </Link>
        </div>

        <div className="bg-slate-900/30 border border-slate-800 rounded-2xl p-8 flex flex-col justify-between hover:border-emerald-500/50 transition-all group">
          <div className="space-y-4">
            <div className="w-12 h-12 bg-emerald-500/10 rounded-xl flex items-center justify-center group-hover:bg-emerald-500/20 transition-colors">
              <Bot className="text-emerald-500" />
            </div>
            <h3 className="text-2xl font-bold">AI 봇에게 투자를 맡기세요</h3>
            <p className="text-slate-400 font-medium leading-relaxed">
              모멘텀, 평균 회귀 등 검증된 전략을 사용하여<br />
              24시간 쉬지 않는 자동 투자 엔진을 가동하세요.
            </p>
          </div>
          <Link href="/bots">
            <Button className="mt-8 font-bold gap-2 h-12 px-6 bg-emerald-600 hover:bg-emerald-700 shadow-lg shadow-emerald-900/20">
              봇 관리자로 이동 <ArrowRight size={18} />
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
