'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore, UserRole } from '@/store/useAuthStore';
import { authService } from '@/services/authService';
import { 
  Card, 
  CardContent, 
  CardDescription, 
  CardFooter, 
  CardHeader, 
  CardTitle 
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { 
  Dialog, 
  DialogContent, 
  DialogDescription, 
  DialogFooter, 
  DialogHeader, 
  DialogTitle 
} from '@/components/ui/dialog';
import { toast } from 'sonner';
import { LogIn, ShieldCheck } from 'lucide-react';

export default function LoginPage() {
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);
  
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  // 2FA Modal State
  const [is2faOpen, setIs2faOpen] = useState(false);
  const [pin, setPin] = useState('');
  const [tempData, setTempData] = useState<{ token: string; email: string; role: UserRole } | null>(null);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      // 🚀 실제 백엔드 API 연동
      const data = await authService.login(email, password);
      
      if (data.role === 'ROLE_APPROVED' || data.role === 'ROLE_ADMIN') {
        setTempData(data);
        setIs2faOpen(true);
      } else {
        completeLogin(data);
      }
    } catch (error: any) {
      const message = error.response?.data?.message || '로그인에 실패했습니다. 정보를 확인하세요.';
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  const handle2faVerify = () => {
    // 2FA는 현재 모킹 상태 유지 (PIN: 123456)
    if (pin === '123456') {
      completeLogin(tempData!);
      setIs2faOpen(false);
    } else {
      toast.error('PIN 번호가 일치하지 않습니다.');
    }
  };

  const completeLogin = (data: { token: string; email: string; role: UserRole }) => {
    localStorage.setItem('token', data.token);
    setAuth({ email: data.email, role: data.role });
    toast.success(`${data.email}님, 환영합니다!`);
    router.push('/dashboard');
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-160px)]">
      <Card className="w-full max-w-md bg-slate-900/50 border-slate-800 shadow-xl backdrop-blur-sm">
        <CardHeader className="space-y-1">
          <div className="flex items-center gap-2 mb-2">
            <div className="p-2 bg-primary/10 rounded-lg">
              <LogIn className="w-5 h-5 text-primary" />
            </div>
            <CardTitle className="text-2xl font-bold">로그인</CardTitle>
          </div>
          <CardDescription className="text-slate-400 font-medium">
            CoinVest 시뮬레이션 플랫폼에 접속합니다.
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleLogin}>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email" className="text-slate-300">이메일</Label>
              <Input 
                id="email" 
                type="email" 
                placeholder="piker@example.com" 
                className="bg-slate-950 border-slate-800 focus:ring-primary"
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
                className="bg-slate-950 border-slate-800 focus:ring-primary"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required 
              />
            </div>
          </CardContent>
          <CardFooter>
            <Button className="w-full font-bold h-11" type="submit" disabled={isLoading}>
              {isLoading ? "인증 중..." : "로그인"}
            </Button>
          </CardFooter>
        </form>
      </Card>

      {/* 2FA PIN Modal */}
      <Dialog open={is2faOpen} onOpenChange={setIs2faOpen}>
        <DialogContent className="sm:max-w-md bg-slate-900 border-slate-800">
          <DialogHeader>
            <div className="mx-auto p-3 bg-emerald-500/10 rounded-full w-fit mb-4 border border-emerald-500/20">
              <ShieldCheck className="w-8 h-8 text-emerald-500" />
            </div>
            <DialogTitle className="text-center text-xl font-bold">2차 인증 (2FA)</DialogTitle>
            <DialogDescription className="text-center text-slate-400 font-medium">
              실전 투자 권한 확인을 위해<br />
              이메일로 발송된 6자리 PIN 번호를 입력하세요.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col items-center py-4">
            <Input
              type="text"
              placeholder="000000"
              className="text-center text-3xl font-mono tracking-[0.5em] h-16 bg-slate-950 border-slate-800 focus:ring-emerald-500"
              maxLength={6}
              value={pin}
              onChange={(e) => setPin(e.target.value)}
              autoFocus
            />
            <p className="text-xs text-slate-500 mt-4 font-medium uppercase tracking-widest">
              Mock PIN: 123456
            </p>
          </div>
          <DialogFooter className="sm:justify-center">
            <Button 
              type="button" 
              className="w-full bg-emerald-600 hover:bg-emerald-700 font-bold h-11"
              onClick={handle2faVerify}
            >
              인증 및 시작하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
