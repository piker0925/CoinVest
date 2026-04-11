'use client';

import React, { useEffect, useRef } from 'react';
import { createChart, ColorType, IChartApi } from 'lightweight-charts';

// Mock Candle Data
const initialData = [
  { time: '2026-04-01', open: 95000000, high: 96500000, low: 94500000, close: 96000000 },
  { time: '2026-04-02', open: 96000000, high: 97800000, low: 95800000, close: 97200000 },
  { time: '2026-04-03', open: 97200000, high: 97500000, low: 96200000, close: 96800000 },
  { time: '2026-04-04', open: 96800000, high: 98500000, low: 96500000, close: 98200000 },
  { time: '2026-04-05', open: 98200000, high: 99200000, low: 97800000, close: 98800000 },
  { time: '2026-04-06', open: 98800000, high: 98900000, low: 97200000, close: 97800000 },
  { time: '2026-04-07', open: 97800000, high: 99500000, low: 97500000, close: 99100000 },
  { time: '2026-04-08', open: 99100000, high: 99800000, low: 98800000, close: 99400000 },
  { time: '2026-04-09', open: 99400000, high: 99600000, low: 97500000, close: 98200000 },
  { time: '2026-04-10', open: 98200000, high: 98800000, low: 97800000, close: 98200000 },
];

export const CandleChart = () => {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);

  useEffect(() => {
    if (!chartContainerRef.current) return;

    // Strict Mode 중복 렌더링 방지: 이미 차트가 있으면 제거 후 재생성
    if (chartRef.current) {
      chartRef.current.remove();
      chartRef.current = null;
    }

    const chart = createChart(chartContainerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#94a3b8',
      },
      grid: {
        vertLines: { color: '#1e293b' },
        horzLines: { color: '#1e293b' },
      },
      width: chartContainerRef.current.clientWidth,
      height: 400,
      timeScale: { borderColor: '#1e293b' },
      rightPriceScale: { borderColor: '#1e293b' },
    });

    const candleSeries = chart.addCandlestickSeries({
      upColor: '#ef4444',
      downColor: '#3b82f6',
      borderVisible: false,
      wickUpColor: '#ef4444',
      wickDownColor: '#3b82f6',
    });

    candleSeries.setData(initialData);
    chart.timeScale().fitContent();
    chartRef.current = chart;

    const handleResize = () => {
      if (chartContainerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: chartContainerRef.current.clientWidth });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }
    };
  }, []);

  return (
    <div ref={chartContainerRef} className="w-full h-full relative min-h-[400px]" />
  );
};
