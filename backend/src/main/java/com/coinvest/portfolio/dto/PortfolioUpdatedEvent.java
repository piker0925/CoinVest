package com.coinvest.portfolio.dto;

import java.util.List;

/**
 * 포트폴리오 업데이트 시 발생하는 도메인 이벤트.
 * Redis 캐시 및 실시간 평가 엔진에서 사용.
 */
public record PortfolioUpdatedEvent(
    Long portfolioId,
    Long userId,
    List<String> universalCodes,
    UpdateType type
) {
    public enum UpdateType {
        CREATE, UPDATE, DELETE
    }
}
