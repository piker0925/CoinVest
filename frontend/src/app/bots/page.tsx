'use client';

import React, { useState } from 'react';
import { 
  Cpu, 
  Zap, 
  BarChart3, 
  Info,
  History
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { BotCard } from '@/components/features/bots/BotCard';
import { DecisionTimeline } from '@/components/features/bots/DecisionTimeline';

// Mock Data
const botStrategies = [
  { 
    id: 1, 
    name: 'Momentum Master', 
    strategy: 'MOMENTUM', 
    status: 'ACTIVE', 
    roi: 15.4, 
    sharpe: 1.85, 
    trades: 124, 
    assets: ['BTC', 'ETH', 'SOL'],
    description: '강세 추세 종목을 포착하여 돌파 매매를 수행하는 고수익 전략.'
  },
  { 
    id: 2, 
    name: 'Mean Reversion King', 
    strategy: 'MEAN_REVERSION', 
    status: 'PAUSED', 
    roi: 8.2, 
    sharpe: 2.12, 
    trades: 56, 
    assets: ['KOSPI', 'SP500_SIM'],
    description: '과매도 구간의 반등을 노리는 안정적인 변동성 매매 전략.'
  },
  { 
    id: 3, 
    name: 'Baseline Random', 
    strategy: 'RANDOM_BASELINE', 
    status: 'ACTIVE', 
    roi: 2.5, 
    sharpe: 0.45, 
    trades: 312, 
    assets: ['ALL'],
    description: '무작위 매수/매도를 통해 시장 벤치마크와 비교하기 위한 기준점.'
  }
];

const decisionLogs = [
  { time: '14:32:11', symbol: 'BTC', action: 'BUY', reason: 'RSI(30) 이하 과매도 구간 진입 및 5분봉 골든크로스 발생', score: 0.85 },
  { time: '13:15:04', symbol: 'ETH', action: 'SELL', reason: '목표 수익률(5%) 달성 및 단기 저항선 돌파 실패', score: 0.92 },
  { time: '11:45:22', symbol: 'SOL', action: 'HOLD', reason: '보유 수량 대비 잔고 부족으로 추가 매수 유보', score: 0.12 },
  { time: '09:30:11', symbol: 'BTC', action: 'BUY', reason: '볼린저 밴드 하단 터치 및 거래량 급증 포착', score: 0.78 },
];

export default function BotsPage() {
  const [activeBot, setActiveBot] = useState(botStrategies[0]);

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto pb-10">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-primary/10 rounded-lg">
            <Cpu className="w-6 h-6 text-primary" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight">AI 봇 관리 시스템</h2>
        </div>
        <Button className="font-bold h-10 px-6 gap-2">
          <Zap size={16} /> 신규 봇 생성
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {botStrategies.map((bot) => (
          <BotCard 
            key={bot.id} 
            bot={bot} 
            isActive={activeBot.id === bot.id}
            onClick={() => setActiveBot(bot)}
          />
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2 bg-slate-900/50 border-slate-800 h-[400px] flex flex-col overflow-hidden">
          <CardHeader className="pb-3 border-b border-slate-800/50">
            <div className="flex items-center justify-between">
              <CardTitle className="text-md flex items-center gap-2 font-bold text-white">
                <BarChart3 size={18} className="text-primary" />
                Order Decision Logs: {activeBot.name}
              </CardTitle>
              <Badge className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20">Real-time Analysis</Badge>
            </div>
          </CardHeader>
          <div className="flex-1 overflow-hidden">
            <DecisionTimeline logs={decisionLogs} />
          </div>
        </Card>

        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader>
            <CardTitle className="text-md flex items-center gap-2 font-bold text-white">
              <Info size={18} className="text-primary" />
              Strategy Intelligence
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2">
              <h4 className="text-xs font-bold text-slate-500 uppercase tracking-widest">Description</h4>
              <p className="text-sm text-slate-300 leading-relaxed font-medium">
                {activeBot.description}
              </p>
            </div>
            <Separator className="bg-slate-800" />
            <div className="space-y-4">
              <h4 className="text-xs font-bold text-slate-500 uppercase tracking-widest">Real-time Performance</h4>
              <div className="space-y-3 font-mono">
                <div className="flex justify-between items-center">
                  <span className="text-xs text-slate-400">Total PnL</span>
                  <span className="text-sm font-bold text-emerald-500">+4,240,000 KRW</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-slate-400">Win Rate</span>
                  <span className="text-sm font-bold text-white">68.4%</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-slate-400">Max Drawdown</span>
                  <span className="text-sm font-bold text-red-500">-4.2%</span>
                </div>
              </div>
            </div>
            <Button variant="outline" className="w-full border-slate-800 hover:bg-slate-800 gap-2 font-bold">
               <History size={16} /> 상세 리포트 다운로드
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
