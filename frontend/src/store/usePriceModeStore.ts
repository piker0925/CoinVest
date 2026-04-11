import { create } from 'zustand';

type PriceMode = 'DEMO' | 'LIVE';

interface PriceModeState {
  mode: PriceMode;
  setMode: (mode: PriceMode) => void;
}

export const usePriceModeStore = create<PriceModeState>((set) => ({
  mode: 'DEMO', // 기본값은 모의투자
  setMode: (mode) => set({ mode }),
}));
