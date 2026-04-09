package com.coinvest.dashboard.service;

import com.coinvest.dashboard.dto.SystemMetric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 초경량 모니터링 서비스 (Prometheus/Grafana 대체).
 * 1분 주기로 JVM, CPU, DB 지표를 수집하여 Redis에 24시간 보관.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HikariDataSource dataSource;

    private static final String METRIC_KEY = "metrics:system";
    private static final long RETENTION_HOURS = 24;

    /**
     * 매 분 0초마다 시스템 지표 수집 및 저장.
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectMetrics() {
        LocalDateTime now = LocalDateTime.now();
        SystemMetric metric = SystemMetric.builder()
                .timestamp(now)
                .cpuUsage(getCpuUsage())
                .memoryUsed(getMemoryUsed())
                .memoryMax(getMemoryMax())
                .dbActiveConn(dataSource.getHikariPoolMXBean().getActiveConnections())
                .dbIdleConn(dataSource.getHikariPoolMXBean().getIdleConnections())
                .build();

        saveToRedis(metric);
        purgeOldMetrics(now);
    }

    /**
     * 최근 24시간 내의 지표 목록 조회.
     */
    public List<SystemMetric> getRecentMetrics() {
        long minScore = LocalDateTime.now().minusHours(RETENTION_HOURS).toEpochSecond(ZoneOffset.UTC);
        Set<String> jsonMetrics = redisTemplate.opsForZSet().rangeByScore(METRIC_KEY, minScore, Double.MAX_VALUE);

        if (jsonMetrics == null) return List.of();

        return jsonMetrics.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, SystemMetric.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse metric json", e);
                        return null;
                    }
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            java.lang.reflect.Method method = osBean.getClass().getMethod("getCpuLoad");
            method.setAccessible(true);
            Double load = (Double) method.invoke(osBean);
            return load * 100.0;
        } catch (Exception e) {
            log.warn("Failed to get CPU usage via reflection: {}", e.getMessage());
            return -1.0;
        }
    }

    private long getMemoryUsed() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getMemoryMax() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    private void saveToRedis(SystemMetric metric) {
        try {
            String json = objectMapper.writeValueAsString(metric);
            long score = metric.getTimestamp().toEpochSecond(ZoneOffset.UTC);
            redisTemplate.opsForZSet().add(METRIC_KEY, json, score);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize system metric", e);
        }
    }

    private void purgeOldMetrics(LocalDateTime now) {
        long maxScore = now.minusHours(RETENTION_HOURS).toEpochSecond(ZoneOffset.UTC);
        redisTemplate.opsForZSet().removeRangeByScore(METRIC_KEY, 0, maxScore);
    }
}
