package com.coinvest.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 포트폴리오 업데이트 시 발생하는 Kafka 이벤트 DTO.
 * Redis 캐시 및 실시간 평가 엔진에서 사용.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpdatedEvent {

    private Long portfolioId;
    private Long userId;
    private List<String> universalCodes;

    /**
     * 업데이트 유형 (CREATE, UPDATE, DELETE).
     */
    private UpdateType type;

    public enum UpdateType {
        CREATE, UPDATE, DELETE
    }
}
