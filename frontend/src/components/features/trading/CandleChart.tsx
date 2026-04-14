'use client';

import React, {useEffect, useRef} from 'react';
import {CandlestickData, ColorType, createChart, IChartApi, ISeriesApi, Time} from 'lightweight-charts';
import {useQuery} from '@tanstack/react-query';
import {tradingService} from '@/services/tradingService';
import {PriceMode} from '@/store/useAuthStore';

interface CandleChartProps {
    universalCode: string;
    mode: PriceMode;
    tickerPrice?: number;
}

export const CandleChart = ({universalCode, mode, tickerPrice}: CandleChartProps) => {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
    const latestCandleRef = useRef<CandlestickData | null>(null);

    const {data: candles = []} = useQuery({
        queryKey: ['candles', universalCode, mode],
        queryFn: () => tradingService.getCandles(universalCode, mode),
        enabled: !!universalCode,
        staleTime: Infinity,
    });

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

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
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

    useEffect(() => {
        if (!seriesRef.current || candles.length === 0) return;
        seriesRef.current.setData(candles as CandlestickData[]);
        chartRef.current?.timeScale().fitContent();

        const last = candles[candles.length - 1];
        latestCandleRef.current = {
            ...last,
            time: last.time as Time
        };
    }, [candles]);

    useEffect(() => {
        if (!seriesRef.current || tickerPrice == null || latestCandleRef.current == null) return;

        const currentSlotTime = (Math.floor(Date.now() / 1000 / 300) * 300) as Time;
        const latest = latestCandleRef.current;

        let updated: CandlestickData;

        if (currentSlotTime > latest.time) {
            updated = {
                time: currentSlotTime,
                open: latest.close,
                high: tickerPrice,
                low: tickerPrice,
                close: tickerPrice,
            };
        } else {
            updated = {
                time: latest.time,
                open: latest.open,
                high: Math.max(latest.high, tickerPrice),
                low: Math.min(latest.low, tickerPrice),
                close: tickerPrice,
            };
        }

        seriesRef.current.update(updated);
        latestCandleRef.current = updated;
    }, [tickerPrice]);

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
