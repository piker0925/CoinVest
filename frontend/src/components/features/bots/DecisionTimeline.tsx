'use client';

import React from 'react';
import {Pause, TrendingUp} from 'lucide-react';
import {Badge} from '@/components/ui/badge';
import {ScrollArea} from '@/components/ui/scroll-area';

interface DecisionLog {
    time: string;
    action: 'BUY' | 'SELL' | 'HOLD';
    symbol: string;
    score: number;
    reason: string;
}

interface DecisionTimelineProps {
    logs: DecisionLog[];
}

export const DecisionTimeline = ({logs}: DecisionTimelineProps) => {
    return (
        <ScrollArea className="h-full">
            <div className="p-4 space-y-4">
                {logs.map((log, i) => (
                    <div key={i} className="flex gap-4 relative">
                        {i !== logs.length - 1 && (
                            <div className="absolute left-[15px] top-8 bottom-[-16px] w-[1px] bg-slate-800"/>
                        )}
                        <div
                            className={`z-10 w-8 h-8 rounded-full flex items-center justify-center border-2 border-slate-950 ${
                                log.action === 'BUY' ? 'bg-red-500' : log.action === 'SELL' ? 'bg-blue-500' : 'bg-slate-700'
                            }`}>
                            {log.action === 'BUY' ? <TrendingUp size={14} className="text-white"/> :
                                log.action === 'SELL' ? <TrendingUp size={14} className="text-white rotate-180"/> :
                                    <Pause size={14} className="text-white"/>}
                        </div>
                        <div
                            className="flex-1 bg-slate-950/50 p-3 rounded-lg border border-slate-800/50 hover:border-slate-700 transition-colors">
                            <div className="flex items-center justify-between mb-1">
                                <div className="flex items-center gap-2">
                                    <span className="text-xs font-bold text-slate-400">{log.time}</span>
                                    <Badge
                                        className={log.action === 'BUY' ? 'bg-red-500/10 text-red-500 border-red-500/20' : 'bg-blue-500/10 text-blue-500 border-blue-500/20'}>
                                        {log.action} {log.symbol}
                                    </Badge>
                                </div>
                                <div
                                    className="text-[10px] font-mono text-slate-500 font-bold uppercase tracking-widest">
                                    Confidence: {(log.score * 100).toFixed(0)}%
                                </div>
                            </div>
                            <p className="text-xs text-slate-300 leading-relaxed font-medium">
                                {log.reason}
                            </p>
                        </div>
                    </div>
                ))}
            </div>
        </ScrollArea>
    );
};
