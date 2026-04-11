'use client';

import React from 'react';
import {Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip} from 'recharts';
import {ArrowUpRight, PieChart as PieChartIcon, TrendingDown, TrendingUp, Wallet} from 'lucide-react';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Badge} from '@/components/ui/badge';

// Mock Data
const portfolioSummary = {
    totalValue: 124500000,
    totalPnl: 14500000,
    roi: 13.18,
    dailyChange: 1.25,
    availableKrw: 24500000
};

const assetAllocation = [
    {name: 'KRW', value: 24500000, color: '#64748b'},
    {name: 'BTC', value: 68000000, color: '#f59e0b'},
    {name: 'ETH', value: 22000000, color: '#6366f1'},
    {name: 'SOL', value: 10000000, color: '#10b981'},
];

const holdings = [
    {
        symbol: 'BTC',
        name: 'Bitcoin',
        amount: '0.6923',
        avgPrice: '78,450,000',
        currentPrice: '98,200,000',
        pnl: '+13,674,000',
        roi: '+17.4%'
    },
    {
        symbol: 'ETH',
        name: 'Ethereum',
        amount: '4.2150',
        avgPrice: '4,850,000',
        currentPrice: '5,220,000',
        pnl: '+1,559,000',
        roi: '+7.6%'
    },
    {
        symbol: 'SOL',
        name: 'Solana',
        amount: '45.12',
        avgPrice: '215,000',
        currentPrice: '221,600',
        pnl: '+297,792',
        roi: '+3.1%'
    },
];

export default function PortfolioPage() {
    return (
        <div className="space-y-6 max-w-[1400px] mx-auto pb-10">
            <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold tracking-tight">포트폴리오 분석</h2>
                <div className="text-xs text-slate-500 font-medium">최종 업데이트: 14:45:12 (5초 폴링 중)</div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Card className="bg-slate-900/50 border-slate-800 backdrop-blur-sm font-sans">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium text-slate-400">총 자산 (KRW)</CardTitle>
                        <Wallet className="h-4 w-4 text-slate-500"/>
                    </CardHeader>
                    <CardContent>
                        <div className="text-3xl font-bold font-mono text-white">
                            {portfolioSummary.totalValue.toLocaleString()} <span
                            className="text-sm font-normal text-slate-500 ml-1">KRW</span>
                        </div>
                        <p className="text-xs text-slate-500 mt-2 font-medium">
                            주문가능 잔고: {portfolioSummary.availableKrw.toLocaleString()} KRW
                        </p>
                    </CardContent>
                </Card>

                <Card className="bg-slate-900/50 border-slate-800 backdrop-blur-sm">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium text-slate-400">누적 수익률</CardTitle>
                        <TrendingUp className="h-4 w-4 text-emerald-500"/>
                    </CardHeader>
                    <CardContent>
                        <div className="text-3xl font-bold font-mono text-emerald-500">
                            +{portfolioSummary.roi}%
                        </div>
                        <div className="flex items-center gap-1 mt-2 text-emerald-500 text-xs font-bold">
                            <ArrowUpRight size={14}/>
                            <span>+{portfolioSummary.totalPnl.toLocaleString()} KRW</span>
                        </div>
                    </CardContent>
                </Card>

                <Card className="bg-slate-900/50 border-slate-800 backdrop-blur-sm">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium text-slate-400">당일 수익률</CardTitle>
                        <TrendingDown className="h-4 w-4 text-red-500"/>
                    </CardHeader>
                    <CardContent>
                        <div className="text-3xl font-bold font-mono text-emerald-500">
                            +{portfolioSummary.dailyChange}%
                        </div>
                        <p className="text-xs text-slate-500 mt-2 font-medium uppercase tracking-widest">
                            Market Benchmarked
                        </p>
                    </CardContent>
                </Card>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
                <Card className="lg:col-span-2 bg-slate-900/50 border-slate-800 flex flex-col">
                    <CardHeader>
                        <CardTitle className="text-md flex items-center gap-2">
                            <PieChartIcon size={18} className="text-primary"/>
                            자산 비중 (Asset Allocation)
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="flex-1 min-h-[300px]">
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie
                                    data={assetAllocation}
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={60}
                                    outerRadius={100}
                                    paddingAngle={5}
                                    dataKey="value"
                                >
                                    {/* 지시하신 대로 Cell 매핑 로직을 명시적으로 작성하여 색상 버그 수정 */}
                                    {assetAllocation.map((entry, index) => (
                                        <Cell key={`cell-${index}`} fill={entry.color} stroke="none"/>
                                    ))}
                                </Pie>
                                <Tooltip
                                    contentStyle={{
                                        backgroundColor: '#0f172a',
                                        border: '1px solid #1e293b',
                                        borderRadius: '8px'
                                    }}
                                    itemStyle={{color: '#f8fafc'}}
                                    formatter={(value: any) => (Number(value)).toLocaleString() + ' KRW'}
                                />
                                <Legend iconType="circle"/>
                            </PieChart>
                        </ResponsiveContainer>
                    </CardContent>
                </Card>

                <Card className="lg:col-span-3 bg-slate-900/50 border-slate-800">
                    <CardHeader>
                        <CardTitle className="text-md flex items-center gap-2">
                            <TrendingUp size={18} className="text-primary"/>
                            보유 종목 상세
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <Table>
                            <TableHeader className="bg-slate-950/50 border-slate-800">
                                <TableRow>
                                    <TableHead>종목</TableHead>
                                    <TableHead className="text-right">보유수량</TableHead>
                                    <TableHead className="text-right">매수평단/현재가</TableHead>
                                    <TableHead className="text-right">평가손익/수익률</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {holdings.map((asset) => (
                                    <TableRow key={asset.symbol} className="border-slate-800/50 hover:bg-slate-800/30">
                                        <TableCell>
                                            <div className="font-bold text-white">{asset.symbol}</div>
                                            <div className="text-[10px] text-slate-500">{asset.name}</div>
                                        </TableCell>
                                        <TableCell
                                            className="text-right font-mono text-slate-300">{asset.amount}</TableCell>
                                        <TableCell className="text-right">
                                            <div className="text-xs font-mono text-slate-400">{asset.avgPrice}</div>
                                            <div
                                                className="text-sm font-mono text-white font-bold">{asset.currentPrice}</div>
                                        </TableCell>
                                        <TableCell className="text-right">
                                            <div
                                                className="text-sm font-mono text-emerald-500 font-bold">{asset.pnl}</div>
                                            <Badge variant="outline"
                                                   className="text-[10px] bg-emerald-500/10 text-emerald-500 border-emerald-500/20 py-0">
                                                {asset.roi}
                                            </Badge>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
