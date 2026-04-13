import apiClient from './apiClient';
import {PriceMode} from '@/store/useAuthStore';

export interface AssetResponse {
    id: number;
    universalCode: string;
    name: string;
    assetClass: 'CRYPTO' | 'US_STOCK' | 'KR_STOCK' | 'US_ETF' | 'KR_ETF';
    quoteCurrency: 'KRW' | 'USD';
    feeRate: number;
    isDemo: boolean;
}

export const assetService = {
    async getAssets(mode: PriceMode = 'DEMO'): Promise<AssetResponse[]> {
        const response = await apiClient.get('/assets', {params: {mode}});
        return response.data.data;
    },
};
