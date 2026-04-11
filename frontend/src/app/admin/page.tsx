'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/useAuthStore';
import { 
  LineChart, 
  Line, 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';
import { 
  Server, 
  Cpu, 
  Database, 
  Activity, 
  ShieldAlert,
  HardDrive
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';

// Mock System Metrics (Last 20 minutes)
const systemMetrics = Array.from({ length: 20 }).map((_, i) => ({
  time: `${14}:${i.toString().padStart(2, '0')}`,
  cpu: Math.floor(Math.random() * 30) + 10,
  memory: (Math.random() * 2 + 1.5).toFixed(1),
  dbPool: Math.floor(Math.random() * 5) + 2,
}));

export default function AdminPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const [isAuthorized, setIsAuthorized] = useState(false);

  useEffect(() => {
    if (!user || user.role !== 'ROLE_ADMIN') {
      toast.error('접근 권한이 없습니다. (ROLE_ADMIN 전용)');
      router.push('/dashboard');
    } else {
      setIsAuthorized(true);
    }
  }, [user, router]);

  if (!isAuthorized) return null;

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto pb-10">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-red-500/10 rounded-lg">
            <Server className="w-6 h-6 text-red-500" />
          </div>
          <div>
            <h2 className="text-2xl font-bold tracking-tight">시스템 모니터링</h2>
            <p className="text-sm text-slate-500 font-medium">Oracle Cloud ARM64 인프라 상태 (Live)</p>
          </div>
        </div>
        <Badge variant="outline" className="bg-red-500/10 text-red-500 border-red-500/20 gap-2 h-9 px-4 uppercase tracking-widest font-bold">
          <Activity size={14} className="animate-pulse" /> System Live
        </Badge>
      </div>

      {/* 1. Quick Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: 'CPU Usage', value: '18.4%', icon: Cpu, color: 'text-emerald-500' },
          { label: 'JVM Heap', value: '1.2 / 4 GB', icon: Activity, color: 'text-blue-500' },
          { label: 'Active DB Pool', value: '4 / 10', icon: Database, color: 'text-amber-500' },
          { label: 'Disk IO', value: 'Normal', icon: HardDrive, color: 'text-slate-400' },
        ].map((stat, i) => (
          <Card key={i} className="bg-slate-900/50 border-slate-800">
            <CardContent className="pt-6">
              <div className="flex items-center justify-between mb-2">
                <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">{stat.label}</p>
                <stat.icon className={`w-4 h-4 ${stat.color}`} />
              </div>
              <p className="text-2xl font-mono font-bold text-white">{stat.value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 2. Real-time Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* CPU Chart */}
        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader>
            <CardTitle className="text-sm flex items-center gap-2">
              <Cpu size={16} className="text-red-500" /> CPU Usage 추이 (%)
            </CardTitle>
          </CardHeader>
          <CardContent className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={systemMetrics}>
                <defs>
                  <linearGradient id="colorCpu" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#ef4444" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="time" stroke="#475569" fontSize={10} tickLine={false} axisLine={false} />
                <YAxis stroke="#475569" fontSize={10} tickLine={false} axisLine={false} domain={[0, 100]} />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: '8px' }}
                  itemStyle={{ color: '#ef4444' }}
                />
                <Area type="monotone" dataKey="cpu" stroke="#ef4444" fillOpacity={1} fill="url(#colorCpu)" />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Memory Chart */}
        <Card className="bg-slate-900/50 border-slate-800">
          <CardHeader>
            <CardTitle className="text-sm flex items-center gap-2">
              <Activity size={16} className="text-blue-500" /> JVM Heap Memory (GB)
            </CardTitle>
          </CardHeader>
          <CardContent className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={systemMetrics}>
                <defs>
                  <linearGradient id="colorMem" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                <XAxis dataKey="time" stroke="#475569" fontSize={10} tickLine={false} axisLine={false} />
                <YAxis stroke="#475569" fontSize={10} tickLine={false} axisLine={false} domain={[0, 4]} />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: '8px' }}
                  itemStyle={{ color: '#3b82f6' }}
                />
                <Area type="monotone" dataKey="memory" stroke="#3b82f6" fillOpacity={1} fill="url(#colorMem)" />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>

      {/* 3. Security Warning Area */}
      <Card className="border-red-900/50 bg-red-950/10">
        <CardHeader className="flex flex-row items-center gap-3 space-y-0">
          <ShieldAlert className="text-red-500" />
          <div>
            <CardTitle className="text-md text-red-500">Security Alert</CardTitle>
            <CardDescription className="text-red-500/70 font-medium">최근 24시간 내 ROLE_APPROVED 유저의 2차 인증 시도가 12회 발생했습니다.</CardDescription>
          </div>
        </CardHeader>
      </Card>
    </div>
  );
}
