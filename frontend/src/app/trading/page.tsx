'use client';

import React from 'react';
import { 
  Search, 
  Clock,
  ArrowRightLeft
} from 'lucide-react';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { CandleChart } from '@/components/features/trading/CandleChart';
import { Orderbook } from '@/components/features/trading/Orderbook';
import { OrderForm } from '@/components/features/trading/OrderForm';
import { useQuery } from '@tanstack/react-query';
import { tradingService } from '@/services/tradingService';
import { useAuthStore } from '@/store/useAuthStore';

// Mock Trade History (UI 유지용)
const tradeHistory = [
  { time: '14:22:45', price: 98200000, quantity: 0.012, type: 'BUY' },
  { time: '14:22:43', price: 98200000, quantity: 0.005, type: 'SELL' },
  { time: '14:22:40', price: 98190000, quantity: 0.231, type: 'BUY' },
  { time: '14:22:38', price: 98210000, quantity: 0.054, type: 'BUY' },
  { time: '14:22:35', price: 98200000, quantity: 0.112, type: 'SELL' },
];

export default function TradingPage() {
  const { mode } = useAuthStore();
  const currentSymbol = 'CRYPTO:BTC';

  // 🚀 Real-time Ticker Data (5s Polling)
  const { data: tickerPrice } = useQuery({
    queryKey: ['ticker', currentSymbol, mode],
    queryFn: () => tradingService.getTicker(currentSymbol, mode),
    refetchInterval: 5000,
    initialData: 98200000,
  });

  // 🚀 Real-time Orderbook Data (5s Polling)
  const { data: orderbook } = useQuery({
    queryKey: ['orderbook', currentSymbol, mode],
    queryFn: () => tradingService.getOrderbook(currentSymbol, mode),
    refetchInterval: 5000,
    initialData: {
      universalCode: currentSymbol,
      sells: [
        { price: 98450000, quantity: 0.12, ratio: 45 },
        { price: 98400000, quantity: 0.05, ratio: 20 },
        { price: 98350000, quantity: 0.32, ratio: 85 },
        { price: 98300000, quantity: 0.18, ratio: 60 },
        { price: 98250000, quantity: 0.08, ratio: 30 },
      ],
      buys: [
        { price: 98150000, quantity: 0.15, ratio: 50 },
        { price: 98100000, quantity: 0.42, ratio: 95 },
        { price: 98050000, quantity: 0.22, ratio: 70 },
        { price: 98000000, quantity: 0.09, ratio: 35 },
        { price: 97950000, quantity: 0.11, ratio: 40 },
      ]
    }
  });

  return (
    <div className="grid grid-cols-[280px_1fr_320px] gap-2 h-[calc(100vh-140px)] min-h-[600px]">
      
      {/* 1. Left: Asset List */}
      <div className="bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
        <div className="p-3 border-b border-slate-800">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-500" />
            <Input 
              placeholder="자산 검색" 
              className="pl-9 h-9 bg-slate-950 border-slate-800 text-sm"
            />
          </div>
        </div>
        <ScrollArea className="flex-1">
          <div className="divide-y divide-slate-800/50">
            {['BTC', 'ETH', 'XRP', 'SOL', 'AAPL', 'TSLA', 'NVDA', 'KOSPI'].map((symbol) => (
              <div key={symbol} className="p-3 hover:bg-slate-800/50 cursor-pointer transition-colors flex justify-between items-center">
                <div>
                  <div className="font-bold text-sm text-white">{symbol}</div>
                  <div className="text-[10px] text-slate-500">Bitcoin / CRYPTO</div>
                </div>
                <div className="text-right">
                  <div className="text-sm font-mono text-red-500">+2.45%</div>
                  <div className="text-[10px] text-slate-400 font-mono">{symbol === 'BTC' ? tickerPrice?.toLocaleString() : '98,200,000'}</div>
                </div>
              </div>
            ))}
          </div>
        </ScrollArea>
      </div>

      {/* 2. Center: Chart & History */}
      <div className="flex flex-col gap-2 overflow-hidden">
        <div className="flex-[2] bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
          <div className="p-3 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-bold text-white">BTC/KRW</h2>
              <span className="text-xl font-mono text-red-500 font-bold">{tickerPrice?.toLocaleString()}</span>
              <span className="text-sm font-mono text-red-500">+1,420,000 (+1.48%)</span>
            </div>
          </div>
          <div className="flex-1 bg-slate-950/20">
            <CandleChart />
          </div>
        </div>
        
        <div className="flex-1 bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
          <div className="p-2 px-3 border-b border-slate-800 flex items-center gap-2">
            <Clock size={14} className="text-slate-500" />
            <span className="text-xs font-bold text-slate-400">체결 내역</span>
          </div>
          <ScrollArea className="flex-1">
            <table className="w-full text-[11px] text-slate-400">
              <thead className="bg-slate-950/50 sticky top-0 border-b border-slate-800/50">
                <tr className="h-8">
                  <th className="font-medium px-3 text-left">시간</th>
                  <th className="font-medium px-3 text-right">체결가</th>
                  <th className="font-medium px-3 text-right">수량</th>
                  <th className="font-medium px-3 text-right">체결액</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {tradeHistory.map((trade, i) => (
                  <tr key={i} className="h-7 border-b border-slate-800/20 hover:bg-slate-800/20 transition-colors">
                    <td className="px-3 text-slate-500">{trade.time}</td>
                    <td className={`px-3 text-right font-bold ${trade.type === 'BUY' ? 'text-red-500' : 'text-blue-500'}`}>
                      {trade.price.toLocaleString()}
                    </td>
                    <td className="px-3 text-right text-slate-300">{trade.quantity}</td>
                    <td className="px-3 text-right text-slate-300">{(trade.price * trade.quantity).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ScrollArea>
        </div>
      </div>

      {/* 3. Right: Orderbook & Form */}
      <div className="flex flex-col gap-2 overflow-hidden">
        <div className="flex-[3] bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
          <div className="p-2 px-3 border-b border-slate-800 flex items-center gap-2">
            <ArrowRightLeft size={14} className="text-slate-500" />
            <span className="text-xs font-bold text-slate-400">호가창</span>
          </div>
          <Orderbook data={orderbook} />
        </div>

        <div className="flex-[2] bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
          <OrderForm />
        </div>
      </div>

    </div>
  );
}
