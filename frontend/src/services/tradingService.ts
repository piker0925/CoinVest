import apiClient from './apiClient';
import { PriceMode } from '@/store/useAuthStore';

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

export const tradingService = {
  /**
   * 자산 현재가 조회 (Ticker)
   */
  async getTicker(universalCode: string, mode: PriceMode = 'LIVE'): Promise<number> {
    const response = await apiClient.get('/price/ticker', {
      params: { universalCode, mode }
    });
    return response.data.data;
  },

  /**
   * 호가창 데이터 조회 (Orderbook)
   */
  async getOrderbook(universalCode: string, mode: PriceMode = 'LIVE'): Promise<OrderbookResponse> {
    const response = await apiClient.get('/price/orderbook', {
      params: { universalCode, mode }
    });
    return response.data.data;
  }
};
