# ============================================================
# Stage 1: Build
# ============================================================
# node:20은 EOL(2026-04). Next 16 요구사항은 >=20.9.0 이므로 현행 LTS인 22로 올린다.
FROM node:22-alpine AS builder
WORKDIR /app

# corepack 대신 직접 설치한다. corepack의 서명 검증이 신규 pnpm 릴리스에서
# 깨진 전례가 있어, 버전 고정 설치가 재현성 면에서 안전하다.
# 버전은 package.json의 packageManager 필드와 반드시 일치시킬 것.
RUN npm install -g pnpm@11.24.0

# 의존성 레이어 캐싱: 소스보다 먼저 매니페스트만 복사한다.
# pnpm-workspace.yaml 누락 주의 — overrides(postcss/sharp 보안 하한)와
# allowBuilds가 여기 들어있어서, 빠지면 취약 버전으로 해석되고 빌드도 깨진다.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# BuildKit 캐시 마운트로 pnpm 스토어를 빌드 간에 재사용한다.
# 없으면 재빌드마다 의존성 전체(400개 이상)를 다시 내려받는다.
# 경로는 컨테이너 안 pnpm이 실제로 쓰는 스토어 위치다.
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY . .

# 빌드 타임 환경 변수 (.env.production 참조)
ENV NEXT_PUBLIC_API_URL=/api/v1
RUN pnpm run build

# ============================================================
# Stage 2: Runner
# ============================================================
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production

# standalone 서버는 기본적으로 localhost에 바인딩한다.
# 컨테이너 안에서 localhost면 nginx 컨테이너가 접근하지 못하므로 0.0.0.0으로 연다.
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

# 루트로 돌리지 않는다.
RUN addgroup -g 1001 -S nodejs && adduser -u 1001 -S nextjs -G nodejs

# next.config.ts의 output:"standalone"이 만들어낸 자립 번들.
# 실제로 쓰이는 모듈만 추려 담기 때문에 node_modules를 통째로 복사할 필요가 없다
# (기존 방식은 eslint/typescript 등 devDependencies까지 프로덕션 이미지에 실렸다).
# static과 public은 번들에 포함되지 않으므로 별도로 복사해야 한다.
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs
EXPOSE 3000

CMD ["node", "server.js"]
