# CoinVest: 암호화폐 포트폴리오 리밸런싱 엔진

> Spring Boot 기반 실시간 가격 모니터링 및 자동 리밸런싱 알림 시스템

## 빠른 시작

```bash
# 빌드
./gradlew build

# 로컬 실행 (Docker Compose)
docker compose -f docker-compose.demo.yml up

# 테스트
./gradlew test
```

## 프로젝트 아키텍처

**상세 아키텍처, 설계 결정, 6개월 로드맵:**
→ [`docs/architecture.md`](docs/architecture.md) 참조

## 기술 스택

- **백엔드**: Spring Boot 3.4, Java 21 (Virtual Thread)
- **메시징**: Kafka (KRaft 모드, Zookeeper 제거)
- **DB**: PostgreSQL (월별 파티셔닝, BRIN 인덱스)
- **캐시**: Redis (다중 전략 TTL)
- **실시간**: WebSocket + Server-Sent Events (SSE)

## 핵심 기능

- Upbit WebSocket을 통한 실시간 가격 스트리밍
- 500ms 윈도우 기반 포트폴리오 평가
- 임계치(5%) 기반 리밸런싱 알림
- Spring Batch 일간 OHLCV 롤업
- Discord Webhook 연동

## 개발 계획

- **Phase 1-6**: 6개월 로드맵 (24주)
- **환경별 설정**: `local`, `demo`, `prod`
- **모니터링**: Virtual Thread Pinning 감지, Kafka 컨슈머 랙 추적