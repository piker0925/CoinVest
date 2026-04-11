'use client';

import React, { useState, useMemo } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';

export const OrderForm = () => {
  const [price, setPrice] = useState('98200000');
  const [quantity, setQuantity] = useState('');
  
  const total = useMemo(() => {
    const p = parseFloat(price) || 0;
    const q = parseFloat(quantity) || 0;
    return (p * q).toLocaleString();
  }, [price, quantity]);

  return (
    <Tabs defaultValue="buy" className="w-full h-full flex flex-col">
      <TabsList className="grid w-full grid-cols-2 bg-slate-950 rounded-none h-11 border-b border-slate-800">
        <TabsTrigger 
          value="buy" 
          className="data-[state=active]:bg-red-600 data-[state=active]:text-white rounded-none font-bold text-xs"
        >
          매수
        </TabsTrigger>
        <TabsTrigger 
          value="sell" 
          className="data-[state=active]:bg-blue-600 data-[state=active]:text-white rounded-none font-bold text-xs"
        >
          매도
        </TabsTrigger>
      </TabsList>
      <TabsContent value="buy" className="m-0 p-4 space-y-4 flex-1 flex flex-col">
        <div className="space-y-3 flex-1">
          <div className="flex items-center justify-between text-[11px] font-bold text-slate-500 uppercase">
            <span>주문가능</span>
            <span className="text-white">1,240,000 KRW</span>
          </div>
          <div className="space-y-1">
            <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">매수가격 (KRW)</div>
            <Input 
              type="text" 
              value={price} 
              onChange={(e) => setPrice(e.target.value)}
              className="h-10 bg-slate-950 border-slate-800 font-mono font-bold"
            />
          </div>
          <div className="space-y-1">
            <div className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">주문수량 (BTC)</div>
            <Input 
              type="text" 
              placeholder="0.0000" 
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              className="h-10 bg-slate-950 border-slate-800 font-mono font-bold"
            />
          </div>
          <div className="grid grid-cols-4 gap-1">
            {['10%', '25%', '50%', '100%'].map((p) => (
              <Button key={p} variant="outline" className="h-7 text-[10px] bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700">
                {p}
              </Button>
            ))}
          </div>
          <Separator className="bg-slate-800 my-2" />
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-400">예상총액</span>
            <span className="text-lg font-mono font-bold text-red-500">{total} <span className="text-xs">KRW</span></span>
          </div>
        </div>
        <Button className="w-full h-12 bg-red-600 hover:bg-red-700 font-bold text-md shadow-lg shadow-red-900/20">
          매수하기
        </Button>
      </TabsContent>
      <TabsContent value="sell" className="m-0 p-4 flex-1">
        <div className="h-full flex items-center justify-center text-slate-500 text-sm italic font-medium">
          매도 폼 준비 중...
        </div>
      </TabsContent>
    </Tabs>
  );
};
