'use client';

import React, {useEffect, useState} from 'react';
import {BarChart3, Cpu, Info, Zap} from 'lucide-react';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Separator} from '@/components/ui/separator';
import {Skeleton} from '@/components/ui/skeleton';
import {ApiErrorFallback} from '@/components/ui/ApiErrorFallback';
import {BotCard} from '@/components/features/bots/BotCard';
import {useQuery} from '@tanstack/react-query';
import {botService, BotSummaryResponse} from '@/services/botService';

const STRATEGY_DESCRIPTIONS: Record<string, string> = {
    MOMENTUM: '강세 추세 종목을 포착하여 돌파 매매를 수행하는 고수익 전략.',
    MEAN_REVERSION: '과매도 구간의 반등을 노리는 안정적인 변동성 매매 전략.',
    RANDOM_BASELINE: '무작위 매수/매도를 통해 시장 벤치마크와 비교하기 위한 기준점.',
};

export default function BotsPage() {
    const [selectedBotId, setSelectedBotId] = useState<number | null>(null);

    const {
        data: bots = [] as BotSummaryResponse[],
        isLoading,
        isError,
        refetch,
    } = useQuery<BotSummaryResponse[]>({
        queryKey: ['bots'],
        queryFn: () => botService.getBots(),
        refetchInterval: false,
        staleTime: 30_000,
    });

    useEffect(() => {
        if (bots.length > 0 && selectedBotId === null) {
            setSelectedBotId(bots[0].id);
        }
    }, [bots, selectedBotId]);

    const selectedBot = bots.find((b) => b.id === selectedBotId) ?? bots[0] ?? null;

    const {data: report, isLoading: isReportLoading} = useQuery({
        queryKey: ['bot-report', selectedBotId],
        queryFn: () => botService.getReport(selectedBotId!, 'ALL'),
        enabled: selectedBotId != null,
        staleTime: 30_000,
    });

    return (
        <div className="space-y-6 max-w-[1400px] mx-auto pb-10">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-primary/10 rounded-lg">
                        <Cpu className="w-6 h-6 text-primary"/>
                    </div>
                    <h2 className="text-2xl font-bold tracking-tight">AI 봇 관리 시스템</h2>
                </div>
                <Button className="font-bold h-10 px-6 gap-2" disabled>
                    <Zap size={16}/> 신규 봇 생성
                </Button>
            </div>

            {isLoading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {[1, 2, 3].map((i) => <Skeleton key={i} className="h-48 w-full rounded-xl"/>)}
                </div>
            ) : isError ? (
                <ApiErrorFallback message="봇 목록을 불러오지 못했습니다." onRetry={refetch}/>
            ) : bots.length === 0 ? (
                <div className="text-center text-slate-500 py-20 text-sm">등록된 봇이 없습니다.</div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {bots.map((bot) => (
                        <BotCard
                            key={bot.id}
                            bot={bot}
                            isActive={selectedBot?.id === bot.id}
                            onClick={() => setSelectedBotId(bot.id)}
                        />
                    ))}
                </div>
            )}

            {selectedBot && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* 성과 리포트 */}
                    <Card
                        className="lg:col-span-2 bg-slate-900/50 border-slate-800 h-[340px] flex flex-col overflow-hidden">
                        <CardHeader className="pb-3 border-b border-slate-800/50">
                            <div className="flex items-center justify-between">
                                <CardTitle className="text-md flex items-center gap-2 font-bold text-white">
                                    <BarChart3 size={18} className="text-primary"/>
                                    Performance Report: {selectedBot.strategyType}
                                </CardTitle>
                                <Badge className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20">
                                    ALL Period
                                </Badge>
                            </div>
                        </CardHeader>
                        <CardContent className="flex-1 pt-4">
                            {isReportLoading ? (
                                <div className="space-y-3">
                                    {[1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-8 w-full"/>)}
                                </div>
                            ) : !report || report.insufficientData ? (
                                <div className="flex items-center justify-center h-full text-slate-500 text-sm">
                                    데이터 축적 중입니다. 봇이 충분히 거래를 진행한 후 통계가 표시됩니다.
                                </div>
                            ) : (
                                <div className="grid grid-cols-2 gap-4 font-mono">
                                    {[
                                        {
                                            label: 'Return Rate',
                                            value: report.returnRate != null ? `${Number(report.returnRate) >= 0 ? '+' : ''}${Number(report.returnRate).toFixed(2)}%` : '—',
                                            color: Number(report.returnRate) >= 0 ? 'text-emerald-500' : 'text-red-500'
                                        },
                                        {
                                            label: 'Win Rate',
                                            value: report.winRate != null ? `${Number(report.winRate).toFixed(1)}%` : '—',
                                            color: 'text-white'
                                        },
                                        {
                                            label: 'Max Drawdown',
                                            value: report.mdd != null ? `-${Number(report.mdd).toFixed(2)}%` : '—',
                                            color: 'text-red-500'
                                        },
                                        {
                                            label: 'Total Trades',
                                            value: String(report.tradeCount),
                                            color: 'text-slate-300'
                                        },
                                    ].map(({label, value, color}) => (
                                        <div key={label}
                                             className="bg-slate-950/50 p-4 rounded-lg border border-slate-800/50">
                                            <p className="text-[10px] uppercase tracking-wider text-slate-500 font-bold mb-2">{label}</p>
                                            <p className={`text-2xl font-bold ${color}`}>{value}</p>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    {/* 전략 정보 */}
                    <Card className="bg-slate-900/50 border-slate-800">
                        <CardHeader>
                            <CardTitle className="text-md flex items-center gap-2 font-bold text-white">
                                <Info size={18} className="text-primary"/>
                                Strategy Intelligence
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-6">
                            <div className="space-y-2">
                                <h4 className="text-xs font-bold text-slate-500 uppercase tracking-widest">Description</h4>
                                <p className="text-sm text-slate-300 leading-relaxed font-medium">
                                    {STRATEGY_DESCRIPTIONS[selectedBot.strategyType] ?? '전략 정보가 없습니다.'}
                                </p>
                            </div>
                            <Separator className="bg-slate-800"/>
                            <div className="space-y-3">
                                <h4 className="text-xs font-bold text-slate-500 uppercase tracking-widest">Summary</h4>
                                <div className="space-y-2 font-mono text-sm">
                                    {[
                                        {
                                            label: '1M Return',
                                            value: selectedBot.returnRate1M != null ? `${Number(selectedBot.returnRate1M) >= 0 ? '+' : ''}${Number(selectedBot.returnRate1M).toFixed(2)}%` : '—'
                                        },
                                        {
                                            label: '3M Return',
                                            value: selectedBot.returnRate3M != null ? `${Number(selectedBot.returnRate3M) >= 0 ? '+' : ''}${Number(selectedBot.returnRate3M).toFixed(2)}%` : '—'
                                        },
                                        {label: 'Status', value: selectedBot.status},
                                    ].map(({label, value}) => (
                                        <div key={label} className="flex justify-between items-center">
                                            <span className="text-xs text-slate-400">{label}</span>
                                            <span className="text-sm font-bold text-white">{value}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                </div>
            )}
        </div>
    );
}
