'use client';

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Activity } from 'lucide-react';

interface BotCardProps {
  bot: any;
  isActive: boolean;
  onClick: () => void;
}

export const BotCard = ({ bot, isActive, onClick }: BotCardProps) => {
  return (
    <Card 
      className={`cursor-pointer transition-all border-slate-800 ${
        isActive ? 'bg-slate-900 border-primary ring-1 ring-primary/20' : 'bg-slate-900/40 hover:bg-slate-900/60'
      }`}
      onClick={onClick}
    >
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
        <div className="space-y-1">
          <CardTitle className="text-lg font-bold text-white">{bot.name}</CardTitle>
          <CardDescription className="text-xs font-medium text-slate-500">{bot.strategy}</CardDescription>
        </div>
        <Switch 
          checked={bot.status === 'ACTIVE'} 
          className="data-[state=checked]:bg-emerald-500"
          onClick={(e) => e.stopPropagation()}
        />
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex justify-between items-end">
          <div className="space-y-1">
            <p className="text-[10px] uppercase tracking-wider text-slate-500 font-bold">누적 수익률</p>
            <p className={`text-2xl font-mono font-bold ${bot.roi > 0 ? 'text-emerald-500' : 'text-red-500'}`}>
              {bot.roi > 0 ? '+' : ''}{bot.roi}%
            </p>
          </div>
          <div className="text-right">
            <p className="text-[10px] uppercase tracking-wider text-slate-500 font-bold">Sharpe Ratio</p>
            <Badge variant="secondary" className="bg-slate-800 text-slate-300 font-mono mt-1">
              {bot.sharpe}
            </Badge>
          </div>
        </div>
        <div className="space-y-1.5">
          <div className="flex justify-between text-[11px] font-bold">
            <span className="text-slate-500 uppercase">Risk Level</span>
            <span className={bot.sharpe > 1.5 ? 'text-emerald-500' : 'text-yellow-500'}>MEDIUM</span>
          </div>
          <Progress value={bot.sharpe * 40} className="h-1.5 bg-slate-800" />
        </div>
      </CardContent>
      <CardFooter className="pt-2 border-t border-slate-800/50 flex justify-between items-center">
        <div className="flex gap-1">
          {bot.assets.map((asset: string) => (
            <Badge key={asset} variant="outline" className="text-[9px] bg-slate-950 border-slate-800 text-slate-400">
              {asset}
            </Badge>
          ))}
        </div>
        <div className="text-[11px] font-medium text-slate-500 flex items-center gap-1">
          <Activity size={12} /> {bot.trades} Trades
        </div>
      </CardFooter>
    </Card>
  );
};
