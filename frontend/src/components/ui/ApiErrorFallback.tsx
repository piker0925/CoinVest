import {AlertTriangle, RefreshCw} from 'lucide-react';
import {Button} from './button';

interface ApiErrorFallbackProps {
    message?: string;
    onRetry?: () => void;
}

export function ApiErrorFallback({
                                     message = '데이터를 불러오지 못했습니다.',
                                     onRetry,
                                 }: ApiErrorFallbackProps) {
    return (
        <div className="flex flex-col items-center justify-center gap-3 py-12 text-slate-500">
            <AlertTriangle className="w-8 h-8 text-amber-500/70"/>
            <p className="text-sm font-medium">{message}</p>
            {onRetry && (
                <Button variant="outline" size="sm" className="gap-2 border-slate-700" onClick={onRetry}>
                    <RefreshCw size={14}/>
                    다시 시도
                </Button>
            )}
        </div>
    );
}
