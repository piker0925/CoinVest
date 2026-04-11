'use client';

import React from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { TrendingUp } from 'lucide-react';

interface OrderbookProps {
  data: {
    sells: any[];
    buys: any[];
  };
}

export const Orderbook = ({ data }: OrderbookProps) => {
  return (
    <ScrollArea className="flex-1 p-0">
      <div className="flex flex-col text-[11px] font-mono leading-none">
        {/* Sells */}
        {data.sells.map((sell, i) => (
          <div key={`sell-${i}`} className="grid grid-cols-2 h-[34px] group border-b border-slate-800/10 cursor-pointer relative overflow-hidden">
            <div className="flex items-center justify-end px-2 bg-blue-500/[0.03] text-blue-400 border-r border-slate-800/50">
              {sell.price.toLocaleString()}
            </div>
            <div className="flex items-center justify-start px-2 bg-slate-950/30 text-slate-400 relative">
              <div className="absolute left-0 top-0 bottom-0 bg-blue-500/10 transition-all duration-300" style={{ width: `${sell.ratio}%` }} />
              <span className="z-10">{sell.quantity}</span>
            </div>
          </div>
        ))}
        
        {/* Mid Current Price */}
        <div className="h-10 bg-slate-950 flex items-center justify-center border-y border-slate-700 shadow-[inset_0_0_15px_rgba(0,0,0,0.5)]">
          <div className="flex items-center gap-2">
            <span className="text-lg font-bold text-red-500 tracking-tight">98,200,000</span>
            <div className="flex flex-col text-[9px] font-bold">
              <TrendingUp size={10} className="text-red-500" />
              <span className="text-red-500">+1.48%</span>
            </div>
          </div>
        </div>

        {/* Buys */}
        {data.buys.map((buy, i) => (
          <div key={`buy-${i}`} className="grid grid-cols-2 h-[34px] group border-b border-slate-800/10 cursor-pointer relative overflow-hidden">
            <div className="flex items-center justify-end px-2 bg-red-500/[0.03] text-red-400 border-r border-slate-800/50">
              {buy.price.toLocaleString()}
            </div>
            <div className="flex items-center justify-start px-2 bg-slate-950/30 text-slate-400 relative">
              <div className="absolute left-0 top-0 bottom-0 bg-red-500/10 transition-all duration-300" style={{ width: `${buy.ratio}%` }} />
              <span className="z-10">{buy.quantity}</span>
            </div>
          </div>
        ))}
      </div>
    </ScrollArea>
  );
};
