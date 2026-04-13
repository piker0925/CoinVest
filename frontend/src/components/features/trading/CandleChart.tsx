'use client';

import React, {useEffect, useRef} from 'react';
import {ColorType, createChart, IChartApi, ISeriesApi} from 'lightweight-charts';
import {useQuery} from '@tanstack/react-query';
import {tradingService} from '@/services/tradingService';
import {PriceMode} from '@/store/useAuthStore';

interface CandleChartProps {
    universalCode: string;
    mode: PriceMode;
}

export const CandleChart = ({universalCode, mode}: CandleChartProps) => {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);

    const {data: candles = []} = useQuery({
        queryKey: ['candles', universalCode, mode],
        queryFn: () => tradingService.getCandles(universalCode, mode),
        enabled: !!universalCode,
        refetchInterval: 60_000, // 1분 주기 갱신
        staleTime: 55_000,
    });

    // 차트 초기화 (마운트 시 1회)
    useEffect(() => {
        if (!chartContainerRef.current) return;

        if (chartRef.current) {
            chartRef.current.remove();
            chartRef.current = null;
            seriesRef.current = null;
        }

        const chart = createChart(chartContainerRef.current, {
            layout: {
                background: {type: ColorType.Solid, color: 'transparent'},
                textColor: '#94a3b8',
            },
            grid: {
                vertLines: {color: '#1e293b'},
                horzLines: {color: '#1e293b'},
            },
            width: chartContainerRef.current.clientWidth,
            height: 400,
            timeScale: {borderColor: '#1e293b'},
            rightPriceScale: {borderColor: '#1e293b'},
        });

        const candleSeries = (chart as any).addCandlestickSeries({
            upColor: '#ef4444',
            downColor: '#3b82f6',
            borderVisible: false,
            wickUpColor: '#ef4444',
            wickDownColor: '#3b82f6',
        });

        chartRef.current = chart;
        seriesRef.current = candleSeries;

        const handleResize = () => {
            if (chartContainerRef.current && chartRef.current) {
                chartRef.current.applyOptions({width: chartContainerRef.current.clientWidth});
            }
        };

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            if (chartRef.current) {
                chartRef.current.remove();
                chartRef.current = null;
                seriesRef.current = null;
            }
        };
    }, []);

    // 데이터 갱신 시 시리즈 업데이트
    useEffect(() => {
        if (!seriesRef.current || candles.length === 0) return;
        seriesRef.current.setData(candles as any);
        chartRef.current?.timeScale().fitContent();
    }, [candles]);

    return (
        <div className="relative w-full h-full min-h-[400px]">
            <div ref={chartContainerRef} className="w-full h-full"/>
            {candles.length === 0 && (
                <div
                    className="absolute inset-0 flex items-center justify-center text-slate-500 text-sm pointer-events-none">
                    가격 데이터 수집 중입니다...
                </div>
            )}
        </div>
    );
};
