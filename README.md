# CoinVest

개인용 **고충실도 모의투자 + 투자 분석 샌드박스**. 실제 증권사처럼 동작하는 모의 거래(실제 주문만 제외)와 보유·차트·what-if 분석을 목표로 하는, 자바/스프링 백엔드 학습·포트폴리오 프로젝트.

> ⚠️ **상태: 재구축(greenfield) 진행 중.** 초기 프로토타입을 걷어내고 깨끗한 구조로 다시 짓는 중이다. 아래는 목표이며, 구현은 첫 수직 슬라이스(한국주식)부터 순차적으로 쌓는다.

## 무엇을 만드나

- **고충실도 모의 거래** — 시세·정산(T+2)·장운영시간을 실제처럼 재현하되, 실제 주문은 나가지 않는 시뮬레이션.
- **투자 분석** — 보유 현황, 실시간 차트, 변동성, "그때 샀으면 지금 얼마" what-if 백테스팅.
- **최종 목표** — 실거래(Toss OpenAPI) 연동.

## 기술 방향

- **Backend**: Java 21, Spring Boot. 헥사고날(포트/어댑터) 설계 — 시세는 `MarketDataPort`(KIS 어댑터), 주문 실행은 `TradingPort`(Mock 어댑터 → 최종 Toss 실거래 어댑터).
- **데이터**: KIS Open API (국내·해외 시세/차트).
- **인증**: Google OAuth 위임 (직접 구현하지 않음 — 위임이 더 안전하다는 판단).
- **인프라**: PostgreSQL, Redis. 로컬은 docker-compose, 테스트는 Testcontainers (TDD).
- **Frontend**: Next.js (백엔드 코어 이후 착수).

## 로컬 실행

```bash
cp .env.example .env      # KIS 앱키/시크릿 입력
docker compose up -d      # Postgres + Redis (로컬 인프라)
# 백엔드/프론트 실행 방법은 재구축 진행에 따라 추가 예정
```

환경 구성은 `docs/environments.md`(로컬), 설계·의사결정은 저장소 내부 문서로 관리한다.
