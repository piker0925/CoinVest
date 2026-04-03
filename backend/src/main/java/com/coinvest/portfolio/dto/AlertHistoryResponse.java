package com.coinvest.portfolio.dto;

import com.coinvest.portfolio.domain.AlertHistory;
import com.coinvest.portfolio.domain.AlertStatus;
import com.coinvest.portfolio.domain.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 이력 응답 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertHistoryResponse {
    private Long id;
    private String message;
    private AlertType type;
    private AlertStatus status;
    private LocalDateTime createdAt;

    public static AlertHistoryResponse from(AlertHistory history) {
        return AlertHistoryResponse.builder()
                .id(history.getId())
                .message(history.getMessage())
                .type(history.getType())
                .status(history.getStatus())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
