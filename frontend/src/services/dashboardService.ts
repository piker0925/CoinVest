import apiClient from './apiClient';

export interface SystemMetric {
    timestamp: string;
    cpuUsage: number;
    memoryUsed: number;
    memoryMax: number;
    dbActiveConn: number;
    dbIdleConn: number;
}

export const dashboardService = {
    async getSystemMetrics(): Promise<SystemMetric[]> {
        const response = await apiClient.get('/dashboard/admin/metrics');
        return response.data.data;
    },
};
