# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app

# 의존성 설치 최적화
COPY package.json package-lock.json ./
RUN npm ci

# 소스 복사 및 빌드
COPY . .
# 빌드 타임 환경 변수 설정 (.env.production 참조)
ENV NEXT_PUBLIC_API_URL=/api/v1
RUN npm run build

# Stage 2: Runner
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production

# 빌드 결과물 및 필수 파일만 복사
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./package.json

EXPOSE 3000

# Next.js 실행
CMD ["npm", "start"]
