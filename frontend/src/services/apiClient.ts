import axios, {AxiosRequestConfig} from 'axios';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const apiClient = axios.create({
    baseURL: API_BASE_URL,
    timeout: 10000,
    headers: {'Content-Type': 'application/json'},
    withCredentials: true, // HttpOnly refresh_token 쿠키를 자동으로 전송
});

// Request Interceptor: 모든 요청에 Bearer accessToken 주입
apiClient.interceptors.request.use(
    (config) => {
        if (typeof window !== 'undefined') {
            const token = localStorage.getItem('token');
            if (token && config.headers) {
                config.headers.Authorization = `Bearer ${token}`;
            }
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// silent reissue 중 대기 중인 요청 큐
let isRefreshing = false;
let failedQueue: Array<{
    resolve: (value: unknown) => void;
    reject: (reason?: unknown) => void;
    config: AxiosRequestConfig;
}> = [];

function processQueue(error: unknown, newToken: string | null) {
    failedQueue.forEach(({resolve, reject, config}) => {
        if (error) {
            reject(error);
        } else {
            if (config.headers) {
                (config.headers as Record<string, string>).Authorization = `Bearer ${newToken}`;
            }
            resolve(apiClient(config));
        }
    });
    failedQueue = [];
}

// Response Interceptor: 401 시 refresh_token 쿠키로 silent reissue 시도
apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

        if (error.response?.status !== 401 || originalRequest._retry) {
            return Promise.reject(error);
        }

        // reissue 자체가 실패한 경우 무한 루프 방지
        if (originalRequest.url?.includes('/auth/reissue')) {
            if (typeof window !== 'undefined') {
                localStorage.removeItem('token');
                window.location.href = '/login';
            }
            return Promise.reject(error);
        }

        if (isRefreshing) {
            // 재발급 진행 중이면 큐에 적재하여 완료 후 재시도
            return new Promise((resolve, reject) => {
                failedQueue.push({resolve, reject, config: originalRequest});
            });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
            const response = await apiClient.post('/auth/reissue');
            const newToken: string = response.data.data.accessToken;
            localStorage.setItem('token', newToken);
            processQueue(null, newToken);
            if (originalRequest.headers) {
                (originalRequest.headers as Record<string, string>).Authorization = `Bearer ${newToken}`;
            }
            return apiClient(originalRequest);
        } catch (refreshError) {
            processQueue(refreshError, null);
            if (typeof window !== 'undefined') {
                localStorage.removeItem('token');
                window.location.href = '/login';
            }
            return Promise.reject(refreshError);
        } finally {
            isRefreshing = false;
        }
    }
);

export default apiClient;
