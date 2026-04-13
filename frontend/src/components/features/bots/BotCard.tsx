'use client';

import React from 'react';
import {Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle} from '@/components/ui/card';
import {Switch} from '@/components/ui/switch';
import {Badge} from '@/components/ui/badge';
import {Activity} from 'lucide-react';
import {BotSummaryResponse} from '@/services/botService';

const STRATEGY_LABELS: Record<string, string> = {
    MOMENTUM: 'Momentum',
    MEAN_REVERSION: 'Mean Reversion',
    RANDOM_BASELINE: 'Random Baseline',
};

interface BotCardProps {
    bot: BotSummaryResponse;
    isActive: boolean;
    onClick: () => void;
}

export const BotCard = ({bot, isActive, onClick}: BotCardProps) => {
    const roi = bot.returnRateAll != null ? Number(bot.returnRateAll) : null;
    const return1M = bot.returnRate1M != null ? Number(bot.returnRate1M) : null;

    return (
        <Card
            className={`cursor-pointer transition-all border-slate-800 ${
                isActive ? 'bg-slate-900 border-primary ring-1 ring-primary/20' : 'bg-slate-900/40 hover:bg-slate-900/60'
            }`}
            onClick={onClick}
        >
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
                <div className="space-y-1">
                    <CardTitle className="text-lg font-bold text-white">
                        {STRATEGY_LABELS[bot.strategyType] ?? bot.strategyType}
                    </CardTitle>
                    <CardDescription className="text-xs font-medium text-slate-500">
                        {bot.strategyType}
                    </CardDescription>
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
                        <p className="text-[10px] uppercase tracking-wider text-slate-500 font-bold">전체 수익률</p>
                        {roi != null ? (
                            <p className={`text-2xl font-mono font-bold ${roi >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                                {roi >= 0 ? '+' : ''}{roi.toFixed(2)}%
                            </p>
                        ) : (
                            <p className="text-xl font-mono font-bold text-slate-500">—</p>
                        )}
                    </div>
                    <div className="text-right">
                        <p className="text-[10px] uppercase tracking-wider text-slate-500 font-bold">1M 수익률</p>
                        {return1M != null ? (
                            <Badge
                                variant="secondary"
                                className={`font-mono mt-1 ${return1M >= 0 ? 'bg-emerald-500/10 text-emerald-500' : 'bg-red-500/10 text-red-500'}`}
                            >
                                {return1M >= 0 ? '+' : ''}{return1M.toFixed(2)}%
                            </Badge>
                        ) : (
                            <Badge variant="secondary" className="bg-slate-800 text-slate-500 font-mono mt-1">
                                데이터 부족
                            </Badge>
                        )}
                    </div>
                </div>
            </CardContent>
            <CardFooter className="pt-2 border-t border-slate-800/50 flex justify-between items-center">
                <Badge
                    variant="outline"
                    className={`text-[10px] ${
                        bot.status === 'ACTIVE'
                            ? 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
                            : 'bg-slate-700/30 text-slate-400 border-slate-700'
                    }`}
                >
                    {bot.status}
                </Badge>
                <div className="text-[11px] font-medium text-slate-500 flex items-center gap-1">
                    <Activity size={12}/> Bot #{bot.id}
                </div>
            </CardFooter>
        </Card>
    );
};
