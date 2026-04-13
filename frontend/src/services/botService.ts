import apiClient from './apiClient';

export type BotStatus = 'ACTIVE' | 'PAUSED';
export type BotStrategyType = 'MOMENTUM' | 'MEAN_REVERSION' | 'RANDOM_BASELINE';

export interface BotSummaryResponse {
    id: number;
    strategyType: BotStrategyType;
    status: BotStatus;
    returnRate1M: number | null;
    returnRate3M: number | null;
    returnRateAll: number | null;
}

export interface BotReportResponse {
    botId: number;
    period: string;
    returnRate: number | null;
    mdd: number | null;
    winRate: number | null;
    tradeCount: number;
    insufficientData: boolean;
}

export const botService = {
    async getBots(): Promise<BotSummaryResponse[]> {
        const response = await apiClient.get('/bots');
        return response.data.data;
    },

    async getReport(botId: number, period = 'ALL'): Promise<BotReportResponse> {
        const response = await apiClient.get(`/bots/${botId}/report`, {params: {period}});
        return response.data.data;
    },
};
