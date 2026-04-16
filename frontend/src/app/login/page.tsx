'use client';

import React, {useState} from 'react';
import {useRouter} from 'next/navigation';
import {useAuthStore} from '@/store/useAuthStore';
import {authService} from '@/services/authService';
import {Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle,} from '@/components/ui/card';
import {Input} from '@/components/ui/input';
import {Button} from '@/components/ui/button';
import {Label} from '@/components/ui/label';
import {toast} from 'sonner';
import {LogIn} from 'lucide-react';

export default function LoginPage() {
    const router = useRouter();
    const setAuth = useAuthStore((state) => state.setAuth);

    const [isLogin, setIsLogin] = useState(true);
    const [nickname, setNickname] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleAction = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);

        try {
            if (isLogin) {
                const data = await authService.login(email, password);
                localStorage.setItem('token', data.accessToken);
                setAuth({email: data.email, role: data.role});
                toast.success(`${data.email}님, 환영합니다!`);
                router.push('/dashboard');
            } else {
                await authService.signup(email, password, nickname);
                toast.success('회원가입 성공! 이제 로그인하세요.');
                setIsLogin(true);
            }
        } catch (error: unknown) {
            const axiosError = error as { response?: { data?: { message?: string } } };
            const message = axiosError.response?.data?.message || '실패했습니다. 정보를 확인하세요.';
            toast.error(message);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="flex items-center justify-center min-h-[calc(100vh-160px)]">
            <Card className="w-full max-w-md bg-slate-900/50 border-slate-800 shadow-xl backdrop-blur-sm">
                <CardHeader className="space-y-1">
                    <div className="flex items-center gap-2 mb-2">
                        <div className="p-2 bg-primary/10 rounded-lg">
                            <LogIn className="w-5 h-5 text-primary"/>
                        </div>
                        <CardTitle className="text-2xl font-bold">{isLogin ? '로그인' : '회원가입'}</CardTitle>
                    </div>
                    <CardDescription className="text-slate-400 font-medium">
                        {isLogin ? 'CoinVest 시뮬레이션 플랫폼에 접속합니다.' : '신규 계정을 생성하여 투자를 시작하세요.'}
                    </CardDescription>
                </CardHeader>
                <form onSubmit={handleAction}>
                    <CardContent className="space-y-4">
                        {!isLogin && (
                            <div className="space-y-2">
                                <Label htmlFor="nickname">닉네임</Label>
                                <Input
                                    id="nickname"
                                    type="text"
                                    className="bg-slate-950 border-slate-800"
                                    value={nickname}
                                    onChange={(e) => setNickname(e.target.value)}
                                    required
                                />
                            </div>
                        )}
                        <div className="space-y-2">
                            <Label htmlFor="email">이메일</Label>
                            <Input
                                id="email"
                                type="email"
                                className="bg-slate-950 border-slate-800"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="password">비밀번호</Label>
                            <Input
                                id="password"
                                type="password"
                                className="bg-slate-950 border-slate-800"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                            />
                        </div>
                    </CardContent>
                    <CardFooter className="flex flex-col gap-4">
                        <Button className="w-full font-bold h-11" type="submit" disabled={isLoading}>
                            {isLoading ? '처리 중...' : (isLogin ? '로그인' : '가입하기')}
                        </Button>
                        <button 
                            type="button"
                            className="text-sm text-slate-400 hover:text-primary transition-colors"
                            onClick={() => setIsLogin(!isLogin)}
                        >
                            {isLogin ? '계정이 없으신가요? 회원가입' : '이미 계정이 있나요? 로그인'}
                        </button>
                    </CardFooter>
                </form>
            </Card>
        </div>
    );
}
