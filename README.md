# CoinVest : 실시간 암호화폐 포트폴리오 리밸런싱 시스템

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3.x-green.svg)
![React](https://img.shields.io/badge/React-TypeScript-blue.svg)
![Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Driven-black.svg)

> **"사용자가 설정한 자산 비중이 흔들릴 때, 가장 빠르게 감지하고 알림을 보냅니다."**
> 
> CoinVest는 Upbit WebSocket을 통해 실시간으로 암호화폐 가격을 수신하고, Kafka 기반의 이벤트 파이프라인을 통해 사용자의 포트폴리오 비중을 평가하여 **임계치를 초과하는 순간 Discord 웹훅 및 실시간 대시보드로 알림을 전송하는 풀스택 애플리케이션**입니다.

---

## 🌟 핵심 기능 (Core Features)

1. **실시간 가격 스트리밍 (Real-time Pipeline)**
   - Upbit WebSocket API와 연동하여 시장의 가격 변동(Tick)을 즉각적으로 수신.
   - 단절 시 지수 백오프 기반 재연결 및 REST API 폴백(Fallback) 지원.
2. **이벤트 드리븐 포트폴리오 평가 (Event-Driven Rebalancing)**
   - Kafka를 활용하여 가격 수신 파이프라인과 포트폴리오 평가 로직을 완벽히 분리(Decoupling).
   - 500ms 텀블링 윈도우 마이크로 배칭을 통해 불필요한 연산 과부하 방지.
3. **스마트 알림 시스템 (Smart Alert System)**
   - 설정한 비중 편차(임계치, 예: 5%) 초과 시 즉각적인 Discord Webhook 및 SSE 알림 발송.
   - Redis를 활용한 5분 단위 디바운싱(Debouncing)으로 알림 피로도(Alert Fatigue) 방지.
4. **시계열 데이터 롤업 및 분석 (Spring Batch)**
   - 하루 단위로 축적되는 방대한 1분 간격 가격 스냅샷을 야간(03:00) Spring Batch를 통해 일간 OHLCV 데이터로 롤업 및 자동 정리.

---

## 🏗 시스템 아키텍처 (Architecture)

CoinVest는 각 도메인 간의 의존성을 분리하고 확장성을 높이기 위해 **이벤트 드리븐 아키텍처**와 **기능별 패키징(Package by Feature)**을 채택했습니다.

```text
[Upbit WebSocket] ──> [가격 수신 모듈] ──> [Kafka: price.ticker.updated]
                                                     │
               ┌─────────────────────────────────────┴──────────────────────────────┐
               ▼                                                                    ▼
 [포트폴리오 평가 컨슈머] ──────────(임계치 초과)──────────> [Kafka: alert.triggered]  (Redis에 최신 가격 캐싱)
               │                                                                    │
               ▼                                                                    ▼
      [Redis 평가 결과 갱신]                                                [알림 발송 모듈 (Discord/SSE)]
```

### 🛠 기술 스택
- **Backend**: Spring Boot 3.4, Java 21 (Virtual Thread 적용), Spring Batch, Spring Data JPA
- **Frontend**: React, TypeScript, Zustand, TailwindCSS, Recharts
- **Message Broker**: Apache Kafka (KRaft 모드)
- **Database / Cache**: PostgreSQL 16 (BRIN 인덱스, 파티셔닝), Redis 7
- **Infra**: Docker Compose, Nginx, GitHub Actions

---

## 💡 주요 기술적 고민과 해결 (Engineering Decisions)

단순한 구현을 넘어, 엔터프라이즈 환경에서 발생할 수 있는 문제들을 선제적으로 방어했습니다.

- **메모리 최적화**: 1GB RAM(프리티어) 환경에서의 OOM을 방지하기 위해 Zookeeper를 제거한 Kafka KRaft 모드를 사용하고, ZGC 대신 SerialGC를 채택했습니다.
- **도메인 격리**: `price`, `portfolio`, `alert` 도메인을 기능별 패키징(Package by Feature)으로 분리하고, 내부 로직은 `package-private`으로 캡슐화하여 Kafka를 통해서만 비동기 통신하도록 강제했습니다.
- **동시성 모델 보완**: 500명 동시 접속 처리를 위해 Java 21 Virtual Thread를 도입했으며, PostgreSQL JDBC의 고질적인 Thread Pinning 현상을 방지하기 위해 최신 드라이버(42.7.4+) 강제 및 HikariCP 풀 사이즈 조절로 대응했습니다.
- **데이터 볼륨 제어**: 초당 수천 건의 Tick 데이터를 RDBMS에 직접 넣지 않고 1분 단위로 샘플링하여 월별 파티셔닝 구조로 저장. B-Tree 대비 가벼운 BRIN 인덱스를 통해 시계열 데이터 조회 성능을 최적화했습니다.

---

## 🚀 빠른 시작 (Quick Start)

### 요구 사항
- Docker 및 Docker Compose
- JDK 21

### 1. 로컬 환경 실행 (풀 스택 시연 모드)
```bash
# 레포지토리 클론
git clone https://github.com/your-username/CoinVest.git
cd CoinVest

# 백엔드 빌드
cd backend
./gradlew build -x test

# 전체 인프라 기동 (PostgreSQL, Redis, Kafka, Nginx, API x3)
cd ../infra
docker compose -f docker-compose.demo.yml up -d
```
API 서버는 기본적으로 `http://localhost:8080` (Nginx 로드밸런싱)을 통해 접근할 수 있습니다.

### 2. 테스트 실행
모든 핵심 비즈니스 로직은 철저한 단위 테스트 및 통합 테스트(Testcontainers, EmbeddedKafka 활용)로 검증됩니다.
```bash
./gradlew test
```

---

## 📜 라이선스
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
