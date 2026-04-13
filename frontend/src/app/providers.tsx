'use client';

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {useEffect, useState} from 'react';
import {useAuthStore} from '@/store/useAuthStore';

export default function Providers({children}: { children: React.ReactNode }) {
    const [queryClient] = useState(
        () =>
            new QueryClient({
                defaultOptions: {
                    queries: {
                        staleTime: 5 * 1000,
                        refetchInterval: 5 * 1000,
                        retry: 1,
                    },
                },
            })
    );

    const hydrateFromToken = useAuthStore((state) => state.hydrateFromToken);

    // 새로고침 시 localStorage accessToken을 디코딩하여 Zustand 상태 복원
    useEffect(() => {
        hydrateFromToken();
    }, [hydrateFromToken]);

    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
