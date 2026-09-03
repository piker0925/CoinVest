import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 프로덕션 이미지를 위한 자립 실행 번들(.next/standalone)을 만든다.
  // 실제로 쓰이는 모듈만 추려 담으므로 러너 스테이지에 node_modules를 통째로
  // 복사할 필요가 없다 — devDependencies가 프로덕션 이미지로 새는 걸 막고,
  // docker-compose.prod.yml의 300M 메모리 제한에도 여유가 생긴다.
  output: "standalone",
};

export default nextConfig;
