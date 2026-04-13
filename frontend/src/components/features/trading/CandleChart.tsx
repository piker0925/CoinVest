'use client';

import React, {useEffect, useRef} from 'react';
import {ColorType, createChart, IChartApi, ISeriesApi} from 'lightweight-charts';
import {useQuery} from '@tanstack/react-query';
import {tradingService} from '@/services/tradingService';
import {PriceMode} from '@/store/useAuthStore';

interface CandleChartProps {
    universalCode: string;
    mode: PriceMode;
    tickerPrice?: number; // 5초 폴링 중인 현재가 — 최신 캔들 꼬리 실시간 갱신용
}

export const CandleChart = ({universalCode, mode, tickerPrice}: CandleChartProps) => {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
    // 최신 캔들 상태를 ref로 추적 (로컬 update()에 필요)
    const latestCandleRef = useRef<{
        time: number;
        open: number;
        high: number;
        low: number;
        close: number
    } | null>(null);

    // 전체 캔들 배열: 초기 1회만 로드 (재폴링 없음 — 데이터 전송량 최소화)
    // 새 5분봉이 완성될 때 자동 반영: tickerPrice가 새 슬롯으로 넘어가면 update()가 새 캔들을 추가함
    const {data: candles = []} = useQuery({
        queryKey: ['candles', universalCode, mode],
        queryFn: () => tradingService.getCandles(universalCode, mode),
        enabled: !!universalCode,
        staleTime: Infinity, // 전체 배열 재요청 없음 — tickerPrice update()로 실시간 반영
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

    // 초기 데이터 로드 시 전체 배열 세팅
    useEffect(() => {
        if (!seriesRef.current || candles.length === 0) return;
        seriesRef.current.setData(candles as any);
        chartRef.current?.timeScale().fitContent();
        // 최신 캔들 ref 초기화
        const last = candles[candles.length - 1];
        latestCandleRef.current = {...last};
    }, [candles]);

    // tickerPrice 변경 시 최신 캔들만 로컬 update() — 전체 배열 재전송 없음
    useEffect(() => {
        if (!seriesRef.current || tickerPrice == null || latestCandleRef.current == null) return;

        // 현재 5분 슬롯의 시작 시간 (Unix 초)
        const currentSlotTime = Math.floor(Date.now() / 1000 / 300) * 300;
        const latest = latestCandleRef.current;

        let updated: typeof latest;

        if (currentSlotTime > latest.time) {
            // 새 5분봉 시작: 이전 close를 open으로 사용
            updated = {
                time: currentSlotTime,
                open: latest.close,
                high: tickerPrice,
                low: tickerPrice,
                close: tickerPrice,
            };
        } else {
            // 같은 슬롯: 꼬리(high/low/close)만 갱신
            updated = {
                time: latest.time,
                open: latest.open,
                high: Math.max(latest.high, tickerPrice),
                low: Math.min(latest.low, tickerPrice),
                close: tickerPrice,
            };
        }

        seriesRef.current.update(updated as any);
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
