'use client';

import React from 'react';
import {Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip} from 'recharts';
import {ArrowUpRight, PieChart as PieChartIcon, TrendingDown, TrendingUp, Wallet} from 'lucide-react';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {ApiErrorFallback} from '@/components/ui/ApiErrorFallback';
import {useQuery} from '@tanstack/react-query';
import {tradingService} from '@/services/tradingService';
import {useAuthStore} from '@/store/useAuthStore';

const ASSET_COLORS: Record<string, string> = {
    KRW: '#64748b',
    USD: '#3b82f6',
    CRYPTO: '#f59e0b',
    US_STOCK: '#6366f1',
    KR_STOCK: '#10b981',
    US_ETF: '#8b5cf6',
    KR_ETF: '#ec4899',
};

function getAssetColor(universalCode: string): string {
    const prefix = universalCode.split(':')[0];
    return ASSET_COLORS[prefix] ?? '#94a3b8';
}

function getShortCode(universalCode: string): string {
    return universalCode.split(':')[1] ?? universalCode;
}

export default function PortfolioPage() {
    const {mode} = useAuthStore();

    const {
        data: account,
        isLoading: isAccountLoading,
        isError: isAccountError,
        refetch,
    } = useQuery({
        queryKey: ['account'],
        queryFn: () => tradingService.getAccount(),
    });

    const {data: positions = [], isLoading: isPositionsLoading} = useQuery({
        queryKey: ['positions'],
        queryFn: () => tradingService.getPositions(),
    });

    const isLoading = isAccountLoading || isPositionsLoading;

    // 총 자산 = 계좌 총액 (잔고 + 포지션 평가액 합산됨)
    const totalValue = account?.totalAssetsKrw ?? 0;
    const krwBalance = account?.balances.find((b) => b.currency === 'KRW')?.available ?? 0;
    const totalUnrealizedPnl = positions.reduce((s, p) => s + p.unrealizedPnl, 0);
    const totalEvaluation = positions.reduce((s, p) => s + p.evaluationAmount, 0);
    const roiPercent = totalEvaluation > 0 && totalValue > 0
        ? ((totalUnrealizedPnl / (totalValue - totalUnrealizedPnl)) * 100)
        : 0;

    // 파이차트 데이터: KRW 잔고 + 각 포지션 평가액
    const allocationData = [
        ...(krwBalance > 0 ? [{name: 'KRW', value: Math.round(krwBalance), color: ASSET_COLORS['KRW']}] : []),
        ...positions.map((p) => ({
            name: getShortCode(p.universalCode),
            value: Math.round(p.evaluationAmount),
            color: getAssetColor(p.universalCode),
        })),
    ];

    if (isAccountError) {
        return (
            <div className="max-w-[1400px] mx-auto pb-10">
                <ApiErrorFallback message="계좌 정보를 불러오지 못했습니다." onRetry={refetch}/>
            </div>
        );
    }

    return (
        <div className="space-y-6 max-w-[1400px] mx-auto pb-10">
            <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold tracking-tight">포트폴리오 분석</h2>
                <div className="text-xs text-slate-500 font-medium">
                    모드: {mode} · 5초 폴링 중
                </div>
            </div>

            {/* 요약 카드 */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Card className="bg-slate-900/50 border-slate-800 backdrop-blur-sm">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium text-slate-400">총 자산 (KRW)</CardTitle>
                        <Wallet className="h-4 w-4 text-slate-500"/>
                    </CardHeader>
                    <CardContent>
                        {isLoading ? (
                            <Skeleton className="h-9 w-44"/>
                        ) : (
                            <>
                                <div className="text-3xl font-bold font-mono text-white">
                                    {Math.round(totalValue).toLocaleString()}{' '}
                                    <span className="text-sm font-normal text-slate-500 ml-1">KRW</span>
                                </div>
                                <p className="text-xs text-slate-500 mt-2 font-medium">
                                    주문가능 잔고: {Math.round(krwBalance).toLocaleString()} KRW
                                </p>
                            </>
                        )}
                    </CardContent>
                </Card>

                <Card className="bg-slate-900/50 border-slate-800 backdrop-blur-sm">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium text-slate-400">총 미실현 손익</CardTitle>
                        <TrendingUp className="h-4 w-4 text-emerald-500"/>
                    </CardHeader>
                    <CardContent>
                        {isLoading ? (
                            <Skeleton className="h-9 w-36"/>
                        ) : (
                            <>
                                <div
                                    className={`text-3xl font-bold font-mono ${
                                        totalUnrealizedPnl >= 0 ? 'text-emerald-500' : 'text-red-500'
                                    }`}
                                >
                                    {totalUnrealizedPnl >= 0 ? '+' : ''}
                                    {roiPercent.toFixed(2)}%
                                </div>
                                <div className="flex items-center gap-1 mt-2 text-emerald-500 text-xs font-bold">
                                    <ArrowUpRight size={14}/>
                                    <span>
                    {totalUnrealizedPnl >= 0 ? '+' : ''}
                                        {Math.round(totalUnrealizedPnl).toLocaleString()} KRW
                  </span>
                                </div>
                            </>
                        )}
                    </CardContent>
                </Card>

                <Card className="bg-slate-900/50 border-slate-800 backdrop-blur-sm">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium text-slate-400">보유 포지션</CardTitle>
                        <TrendingDown className="h-4 w-4 text-slate-400"/>
                    </CardHeader>
                    <CardContent>
                        {isLoading ? (
                            <Skeleton className="h-9 w-24"/>
                        ) : (
                            <>
                                <div className="text-3xl font-bold font-mono text-white">
                                    {positions.length}{' '}
                                    <span className="text-sm font-normal text-slate-500">종목</span>
                                </div>
                                <p className="text-xs text-slate-500 mt-2 font-medium uppercase tracking-widest">
                                    평가액: {Math.round(totalEvaluation).toLocaleString()} KRW
                                </p>
                            </>
                        )}
                    </CardContent>
                </Card>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
                {/* 자산 비중 파이차트 */}
                <Card className="lg:col-span-2 bg-slate-900/50 border-slate-800 flex flex-col">
                    <CardHeader>
                        <CardTitle className="text-md flex items-center gap-2">
                            <PieChartIcon size={18} className="text-primary"/>
                            자산 비중 (Asset Allocation)
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="flex-1 min-h-[300px]">
                        {isLoading ? (
                            <div className="flex items-center justify-center h-full">
                                <Skeleton className="w-48 h-48 rounded-full"/>
                            </div>
                        ) : allocationData.length === 0 ? (
                            <div className="flex items-center justify-center h-full text-slate-500 text-sm">
                                보유 자산이 없습니다
                            </div>
                        ) : (
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={allocationData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={100}
                                        paddingAngle={5}
                                        dataKey="value"
                                    >
                                        {allocationData.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={entry.color} stroke="none"/>
                                        ))}
                                    </Pie>
                                    <Tooltip
                                        contentStyle={{
                                            backgroundColor: '#0f172a',
                                            border: '1px solid #1e293b',
                                            borderRadius: '8px',
                                        }}
                                        itemStyle={{color: '#f8fafc'}}
                                        formatter={(value) => (Number(value) || 0).toLocaleString() + ' KRW'}
                                    />
                                    <Legend iconType="circle"/>
                                </PieChart>
                            </ResponsiveContainer>
                        )}
                    </CardContent>
                </Card>

                {/* 보유 종목 상세 테이블 */}
                <Card className="lg:col-span-3 bg-slate-900/50 border-slate-800">
                    <CardHeader>
                        <CardTitle className="text-md flex items-center gap-2">
                            <TrendingUp size={18} className="text-primary"/>
                            보유 종목 상세
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        {isLoading ? (
                            <div className="space-y-3">
                                {[1, 2, 3].map((i) => (
                                    <Skeleton key={i} className="h-12 w-full"/>
                                ))}
                            </div>
                        ) : positions.length === 0 ? (
                            <div className="text-center text-slate-500 py-12 text-sm">
                                보유 중인 종목이 없습니다
                            </div>
                        ) : (
                            <Table>
                                <TableHeader className="bg-slate-950/50 border-slate-800">
                                    <TableRow>
                                        <TableHead>종목</TableHead>
                                        <TableHead className="text-right">보유수량</TableHead>
                                        <TableHead className="text-right">매수평단 / 현재가</TableHead>
                                        <TableHead className="text-right">평가손익 / 수익률</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {positions.map((pos) => (
                                        <TableRow key={pos.id} className="border-slate-800/50 hover:bg-slate-800/30">
                                            <TableCell>
                                                <div
                                                    className="font-bold text-white">{getShortCode(pos.universalCode)}</div>
                                                <div className="text-[10px] text-slate-500">{pos.universalCode}</div>
                                            </TableCell>
                                            <TableCell className="text-right font-mono text-slate-300">
                                                {pos.quantity}
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <div className="text-xs font-mono text-slate-400">
                                                    {Math.round(pos.avgBuyPrice).toLocaleString()}
                                                </div>
                                                <div className="text-sm font-mono text-white font-bold">
                                                    {Math.round(pos.currentPrice).toLocaleString()}
                                                </div>
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <div
                                                    className={`text-sm font-mono font-bold ${
                                                        pos.unrealizedPnl >= 0 ? 'text-emerald-500' : 'text-red-500'
                                                    }`}
                                                >
                                                    {pos.unrealizedPnl >= 0 ? '+' : ''}
                                                    {Math.round(pos.unrealizedPnl).toLocaleString()}
                                                </div>
                                                <Badge
                                                    variant="outline"
                                                    className={`text-[10px] py-0 ${
                                                        pos.returnRate >= 0
                                                            ? 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20'
                                                            : 'bg-red-500/10 text-red-500 border-red-500/20'
                                                    }`}
                                                >
                                                    {pos.returnRate >= 0 ? '+' : ''}
                                                    {pos.returnRate.toFixed(2)}%
                                                </Badge>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
