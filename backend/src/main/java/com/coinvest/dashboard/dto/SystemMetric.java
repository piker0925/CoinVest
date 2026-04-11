package com.coinvest.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시스템 지표 DTO.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetric {
    private LocalDateTime timestamp;
    private double cpuUsage;      // CPU 사용률 (%)
    private long memoryUsed;      // JVM 사용 중인 메모리 (MB)
    private long memoryMax;       // JVM 최대 가용 메모리 (MB)
    private int dbActiveConn;     // DB 활성 커넥션 수
    private int dbIdleConn;       // DB 유휴 커넥션 수
}
