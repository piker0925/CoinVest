'use client';

import React, {useEffect} from 'react';
import {useRouter} from 'next/navigation';
import {useAuthStore} from '@/store/useAuthStore';
import {Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,} from 'recharts';
import {Activity, Cpu, Database, HardDrive, Server, ShieldAlert} from 'lucide-react';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Badge} from '@/components/ui/badge';
import {Skeleton} from '@/components/ui/skeleton';
import {ApiErrorFallback} from '@/components/ui/ApiErrorFallback';
import {toast} from 'sonner';
import {useQuery} from '@tanstack/react-query';
import {dashboardService, SystemMetric} from '@/services/dashboardService';

function formatTime(timestamp: string): string {
    const d = new Date(timestamp);
    return d.toLocaleTimeString('ko-KR', {hour: '2-digit', minute: '2-digit'});
}

function toChartPoint(m: SystemMetric) {
    return {
        time: formatTime(m.timestamp),
        cpu: Math.round(m.cpuUsage * 10) / 10,
        memory: Math.round((m.memoryUsed / 1024) * 100) / 100, // MB → GB
        dbPool: m.dbActiveConn,
    };
}

export default function AdminPage() {
    const router = useRouter();
    const {user} = useAuthStore();
    const isAuthorized = user?.role === 'ROLE_ADMIN';

    useEffect(() => {
        if (!user || user.role !== 'ROLE_ADMIN') {
            toast.error('접근 권한이 없습니다. (ROLE_ADMIN 전용)');
            router.push('/dashboard');
        }
    }, [user, router]);

    const {
        data: metrics = [],
        isLoading,
        isError,
        refetch,
    } = useQuery({
        queryKey: ['admin-metrics'],
        queryFn: () => dashboardService.getSystemMetrics(),
        refetchInterval: 60_000, // 1분마다 갱신
        enabled: isAuthorized,
    });

    if (!isAuthorized) return null;

    const chartData = metrics.map(toChartPoint);
    const latest = metrics[metrics.length - 1] ?? null;

    const quickStats = [
        {
            label: 'CPU Usage',
            value: latest ? `${latest.cpuUsage.toFixed(1)}%` : '—',
            icon: Cpu,
            color: 'text-emerald-500',
        },
        {
            label: 'JVM Heap',
            value: latest
                ? `${(latest.memoryUsed / 1024).toFixed(1)} / ${(latest.memoryMax / 1024).toFixed(0)} GB`
                : '—',
            icon: Activity,
            color: 'text-blue-500',
        },
        {
            label: 'Active DB Pool',
            value: latest ? `${latest.dbActiveConn} / ${latest.dbActiveConn + latest.dbIdleConn}` : '—',
            icon: Database,
            color: 'text-amber-500',
        },
        {
            label: 'Disk IO',
            value: 'Normal',
            icon: HardDrive,
            color: 'text-slate-400',
        },
    ];

    return (
        <div className="space-y-6 max-w-[1400px] mx-auto pb-10">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-red-500/10 rounded-lg">
                        <Server className="w-6 h-6 text-red-500"/>
                    </div>
                    <div>
                        <h2 className="text-2xl font-bold tracking-tight">시스템 모니터링</h2>
                        <p className="text-sm text-slate-500 font-medium">Oracle Cloud ARM64 인프라 상태 (Live)</p>
                    </div>
                </div>
                <Badge
                    variant="outline"
                    className="bg-red-500/10 text-red-500 border-red-500/20 gap-2 h-9 px-4 uppercase tracking-widest font-bold"
                >
                    <Activity size={14} className="animate-pulse"/> System Live
                </Badge>
            </div>

            {/* Quick Stats */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                {quickStats.map((stat, i) => (
                    <Card key={i} className="bg-slate-900/50 border-slate-800">
                        <CardContent className="pt-6">
                            <div className="flex items-center justify-between mb-2">
                                <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">{stat.label}</p>
                                <stat.icon className={`w-4 h-4 ${stat.color}`}/>
                            </div>
                            {isLoading ? (
                                <Skeleton className="h-8 w-24"/>
                            ) : (
                                <p className="text-2xl font-mono font-bold text-white">{stat.value}</p>
                            )}
                        </CardContent>
                    </Card>
                ))}
            </div>

            {/* Charts */}
            {isError ? (
                <ApiErrorFallback message="시스템 지표를 불러오지 못했습니다." onRetry={refetch}/>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <Card className="bg-slate-900/50 border-slate-800">
                        <CardHeader>
                            <CardTitle className="text-sm flex items-center gap-2">
                                <Cpu size={16} className="text-red-500"/> CPU Usage 추이 (%)
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="h-[300px]">
                            {isLoading ? (
                                <Skeleton className="h-full w-full"/>
                            ) : (
                                <ResponsiveContainer width="100%" height="100%">
                                    <AreaChart data={chartData}>
                                        <defs>
                                            <linearGradient id="colorCpu" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="5%" stopColor="#ef4444" stopOpacity={0.3}/>
                                                <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
                                            </linearGradient>
                                        </defs>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false}/>
                                        <XAxis dataKey="time" stroke="#475569" fontSize={10} tickLine={false}
                                               axisLine={false}/>
                                        <YAxis stroke="#475569" fontSize={10} tickLine={false} axisLine={false}
                                               domain={[0, 100]}/>
                                        <Tooltip
                                            contentStyle={{
                                                backgroundColor: '#0f172a',
                                                border: '1px solid #1e293b',
                                                borderRadius: '8px'
                                            }}
                                            itemStyle={{color: '#ef4444'}}
                                        />
                                        <Area type="monotone" dataKey="cpu" stroke="#ef4444" fillOpacity={1}
                                              fill="url(#colorCpu)"/>
                                    </AreaChart>
                                </ResponsiveContainer>
                            )}
                        </CardContent>
                    </Card>

                    <Card className="bg-slate-900/50 border-slate-800">
                        <CardHeader>
                            <CardTitle className="text-sm flex items-center gap-2">
                                <Activity size={16} className="text-blue-500"/> JVM Heap Memory (GB)
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="h-[300px]">
                            {isLoading ? (
                                <Skeleton className="h-full w-full"/>
                            ) : (
                                <ResponsiveContainer width="100%" height="100%">
                                    <AreaChart data={chartData}>
                                        <defs>
                                            <linearGradient id="colorMem" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                                                <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                                            </linearGradient>
                                        </defs>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false}/>
                                        <XAxis dataKey="time" stroke="#475569" fontSize={10} tickLine={false}
                                               axisLine={false}/>
                                        <YAxis stroke="#475569" fontSize={10} tickLine={false} axisLine={false}
                                               domain={[0, 4]}/>
                                        <Tooltip
                                            contentStyle={{
                                                backgroundColor: '#0f172a',
                                                border: '1px solid #1e293b',
                                                borderRadius: '8px'
                                            }}
                                            itemStyle={{color: '#3b82f6'}}
                                        />
                                        <Area type="monotone" dataKey="memory" stroke="#3b82f6" fillOpacity={1}
                                              fill="url(#colorMem)"/>
                                    </AreaChart>
                                </ResponsiveContainer>
                            )}
                        </CardContent>
                    </Card>
                </div>
            )}

            <Card className="border-red-900/50 bg-red-950/10">
                <CardHeader className="flex flex-row items-center gap-3 space-y-0">
                    <ShieldAlert className="text-red-500"/>
                    <div>
                        <p className="text-md font-bold text-red-500">Security Alert</p>
                        <p className="text-sm text-red-500/70 font-medium">
                            이 페이지는 ROLE_ADMIN 전용입니다. 모든 접근 시도가 서버에 기록됩니다.
                        </p>
                    </div>
                </CardHeader>
            </Card>
        </div>
    );
}
