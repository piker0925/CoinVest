'use client';

import {useEffect} from 'react';
import {useRouter} from 'next/navigation';
import {useAuthStore} from '@/store/useAuthStore';

export default function Home() {
    const router = useRouter();
    const {user} = useAuthStore();

    useEffect(() => {
        router.replace(user ? '/dashboard' : '/login');
    }, [user, router]);

    return null;
}
