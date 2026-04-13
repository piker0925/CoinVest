'use client';

import React, {useEffect, useState} from 'react';
import {ArrowRightLeft, Clock, Search} from 'lucide-react';
import {Input} from '@/components/ui/input';
import {ScrollArea} from '@/components/ui/scroll-area';
import {Skeleton} from '@/components/ui/skeleton';
import {CandleChart} from '@/components/features/trading/CandleChart';
import {Orderbook} from '@/components/features/trading/Orderbook';
import {OrderForm} from '@/components/features/trading/OrderForm';
import {useQuery} from '@tanstack/react-query';
import {tradingService} from '@/services/tradingService';
import {AssetResponse, assetService} from '@/services/assetService';
import {useAuthStore} from '@/store/useAuthStore';

export default function TradingPage() {
    const {mode} = useAuthStore();
    const [searchQuery, setSearchQuery] = useState('');
    const [currentSymbol, setCurrentSymbol] = useState<string | null>(null);

    // 자산 목록 (모드별)
    const {data: assets = [] as AssetResponse[], isLoading: isAssetsLoading} = useQuery<AssetResponse[]>({
        queryKey: ['assets', mode],
        queryFn: () => assetService.getAssets(mode),
        staleTime: 60_000,
        refetchInterval: false,
    });

    useEffect(() => {
        if (assets.length > 0 && currentSymbol === null) {
            setCurrentSymbol(assets[0].universalCode);
        }
    }, [assets, currentSymbol]);

    const selectedAsset = assets.find((a) => a.universalCode === currentSymbol) ?? assets[0] ?? null;
    const activeSymbol = selectedAsset?.universalCode ?? '';

    const filteredAssets = assets.filter(
        (a) =>
            a.universalCode.toLowerCase().includes(searchQuery.toLowerCase()) ||
            a.name.toLowerCase().includes(searchQuery.toLowerCase())
    );

    // 실시간 Ticker (5초 폴링)
    const {data: tickerPrice} = useQuery({
        queryKey: ['ticker', activeSymbol, mode],
        queryFn: () => tradingService.getTicker(activeSymbol, mode),
        refetchInterval: 5000,
        enabled: !!activeSymbol,
    });

    // 실시간 Orderbook (5초 폴링)
    const {data: orderbook} = useQuery({
        queryKey: ['orderbook', activeSymbol, mode],
        queryFn: () => tradingService.getOrderbook(activeSymbol, mode),
        refetchInterval: 5000,
        enabled: !!activeSymbol,
    });

    // 체결 내역 (최근 20건)
    const {data: tradesData} = useQuery({
        queryKey: ['trades'],
        queryFn: () => tradingService.getTrades(undefined, 20),
        refetchInterval: 5000,
    });

    const trades = tradesData?.content ?? [];
    const shortCode = activeSymbol.split(':')[1] ?? activeSymbol;

    return (
        <div className="grid grid-cols-[280px_1fr_320px] gap-2 h-[calc(100vh-140px)] min-h-[600px]">

            {/* 1. 자산 목록 */}
            <div className="bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
                <div className="p-3 border-b border-slate-800">
                    <div className="relative">
                        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-500"/>
                        <Input
                            placeholder="자산 검색"
                            className="pl-9 h-9 bg-slate-950 border-slate-800 text-sm"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>
                </div>
                <ScrollArea className="flex-1">
                    {isAssetsLoading ? (
                        <div className="p-3 space-y-3">
                            {[1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-12 w-full"/>)}
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-800/50">
                            {filteredAssets.map((asset) => {
                                const code = asset.universalCode.split(':')[1];
                                const isSelected = asset.universalCode === activeSymbol;
                                return (
                                    <div
                                        key={asset.universalCode}
                                        className={`p-3 cursor-pointer transition-colors flex justify-between items-center ${
                                            isSelected ? 'bg-slate-800/70' : 'hover:bg-slate-800/50'
                                        }`}
                                        onClick={() => setCurrentSymbol(asset.universalCode)}
                                    >
                                        <div>
                                            <div className="font-bold text-sm text-white">{code}</div>
                                            <div className="text-[10px] text-slate-500">{asset.name}</div>
                                        </div>
                                        <div className="text-right">
                                            <div className="text-[10px] text-slate-500 font-mono uppercase">
                                                {asset.assetClass}
                                            </div>
                                            {isSelected && tickerPrice != null && (
                                                <div className="text-[10px] text-slate-300 font-mono">
                                                    {Math.round(tickerPrice).toLocaleString()}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </ScrollArea>
            </div>

            {/* 2. 차트 & 체결 내역 */}
            <div className="flex flex-col gap-2 overflow-hidden">
                <div
                    className="flex-[2] bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
                    <div className="p-3 border-b border-slate-800 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <h2 className="text-lg font-bold text-white">
                                {shortCode}/{selectedAsset?.quoteCurrency ?? 'KRW'}
                            </h2>
                            {tickerPrice != null && (
                                <span className="text-xl font-mono text-red-500 font-bold">
                  {Math.round(tickerPrice).toLocaleString()}
                </span>
                            )}
                        </div>
                    </div>
                    <div className="flex-1 bg-slate-950/20">
                        <CandleChart universalCode={activeSymbol} mode={mode}/>
                    </div>
                </div>

                {/* 체결 내역 */}
                <div
                    className="flex-1 bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
                    <div className="p-2 px-3 border-b border-slate-800 flex items-center gap-2">
                        <Clock size={14} className="text-slate-500"/>
                        <span className="text-xs font-bold text-slate-400">내 체결 내역</span>
                    </div>
                    <ScrollArea className="flex-1">
                        <table className="w-full text-[11px] text-slate-400">
                            <thead className="bg-slate-950/50 sticky top-0 border-b border-slate-800/50">
                            <tr className="h-8">
                                <th className="font-medium px-3 text-left">시간</th>
                                <th className="font-medium px-3 text-left">종목</th>
                                <th className="font-medium px-3 text-right">체결가</th>
                                <th className="font-medium px-3 text-right">수량</th>
                            </tr>
                            </thead>
                            <tbody className="font-mono">
                            {trades.length === 0 ? (
                                <tr>
                                    <td colSpan={4} className="text-center py-6 text-slate-600">
                                        체결 내역이 없습니다
                                    </td>
                                </tr>
                            ) : (
                                trades.map((trade) => (
                                    <tr
                                        key={trade.id}
                                        className="h-7 border-b border-slate-800/20 hover:bg-slate-800/20 transition-colors"
                                    >
                                        <td className="px-3 text-slate-500">
                                            {new Date(trade.createdAt).toLocaleTimeString('ko-KR', {
                                                hour: '2-digit',
                                                minute: '2-digit',
                                                second: '2-digit',
                                            })}
                                        </td>
                                        <td className="px-3 text-slate-400">
                                            {trade.universalCode.split(':')[1] ?? trade.universalCode}
                                        </td>
                                        <td className="px-3 text-right font-bold text-slate-200">
                                            {Math.round(trade.price).toLocaleString()}
                                        </td>
                                        <td className="px-3 text-right text-slate-300">{trade.quantity}</td>
                                    </tr>
                                ))
                            )}
                            </tbody>
                        </table>
                    </ScrollArea>
                </div>
            </div>

            {/* 3. 호가창 & 주문폼 */}
            <div className="flex flex-col gap-2 overflow-hidden">
                <div
                    className="flex-[3] bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
                    <div className="p-2 px-3 border-b border-slate-800 flex items-center gap-2">
                        <ArrowRightLeft size={14} className="text-slate-500"/>
                        <span className="text-xs font-bold text-slate-400">호가창</span>
                    </div>
                    <Orderbook data={orderbook} currentPrice={tickerPrice}/>
                </div>
                <div
                    className="flex-[2] bg-slate-900/40 border border-slate-800 rounded-lg flex flex-col overflow-hidden">
                    <OrderForm universalCode={activeSymbol}/>
                </div>
            </div>
        </div>
    );
}
