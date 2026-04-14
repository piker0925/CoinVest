import apiClient from './apiClient';
import {PriceMode} from '@/store/useAuthStore';

export interface OrderbookUnit {
    price: number;
    quantity: number;
    ratio: number;
}

export interface OrderbookResponse {
    universalCode: string;
    sells: OrderbookUnit[];
    buys: OrderbookUnit[];
}

export interface BalanceResponse {
    currency: 'KRW' | 'USD';
    available: number;
    locked: number;
    total: number;
}

export interface AccountResponse {
    totalAssetsKrw: number;
    balances: BalanceResponse[];
}

export interface PositionResponse {
    id: number;
    universalCode: string;
    currency: 'KRW' | 'USD';
    avgBuyPrice: number;
    quantity: number;
    lockedQuantity: number;
    availableQuantity: number;
    realizedPnl: number;
    currentPrice: number;
    evaluationAmount: number;
    unrealizedPnl: number;
    returnRate: number;
}

export interface TradeResponse {
    id: number;
    orderId: number;
    universalCode: string;
    price: number;
    quantity: number;
    fee: number;
    realizedPnl: number;
    createdAt: string;
}

export interface CandleData {
    time: number;  // Unix timestamp (seconds)
    open: number;
    high: number;
    low: number;
    close: number;
}

export const tradingService = {
    async getTicker(universalCode: string, mode: PriceMode = 'LIVE'): Promise<number> {
        const response = await apiClient.get('/price/ticker', {
            params: {universalCode, mode},
        });
        return response.data.data;
    },

    async getOrderbook(universalCode: string, mode: PriceMode = 'LIVE'): Promise<OrderbookResponse> {
        const response = await apiClient.get('/price/orderbook', {
            params: {universalCode, mode},
        });
        return response.data.data;
    },

    async getAccount(): Promise<AccountResponse> {
        const response = await apiClient.get('/trading/account');
        return response.data.data;
    },

    async getPositions(): Promise<PositionResponse[]> {
        const response = await apiClient.get('/trading/positions');
        return response.data.data;
    },

    async getTrades(cursorId?: number, size = 20): Promise<{
        content: TradeResponse[];
        nextCursor: number | null;
        hasNext: boolean
    }> {
        const response = await apiClient.get('/trading/trades', {
            params: {cursorId, size},
        });
        return response.data.data;
    },

    async getCandles(universalCode: string, mode: PriceMode = 'LIVE'): Promise<CandleData[]> {
        const response = await apiClient.get('/price/candles', {
            params: {universalCode, mode},
        });
        return response.data.data;
    },
};
