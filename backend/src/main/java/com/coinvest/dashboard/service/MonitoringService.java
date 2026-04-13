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

import org.springframework.data.redis.core.script.RedisScript;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    // 동일 score 구간의 기존 엔트리 제거 후 신규 삽입 — 원자적 보장 (D4-5)
    private static final RedisScript<Long> UPSERT_METRIC_SCRIPT = RedisScript.of(
            "local score = tonumber(ARGV[1])\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], score, score)\n" +
            "return redis.call('ZADD', KEYS[1], score, ARGV[2])",
            Long.class
    );

    /**
     * 매 분 0초마다 시스템 지표 수집 및 저장.
     */
    @Scheduled(cron = "0 * * * * *")
    public void collectMetrics() {
        // Instant 기반 분 절사: 타임존 오해 없이 정확한 Epoch 계산
        Instant truncatedNow = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime now = LocalDateTime.ofInstant(truncatedNow, ZoneId.systemDefault());

        SystemMetric metric = SystemMetric.builder()
                .timestamp(now)
                .cpuUsage(getCpuUsage())
                .memoryUsed(getMemoryUsed())
                .memoryMax(getMemoryMax())
                .dbActiveConn(dataSource.getHikariPoolMXBean().getActiveConnections())
                .dbIdleConn(dataSource.getHikariPoolMXBean().getIdleConnections())
                .build();

        saveToRedis(metric, truncatedNow.getEpochSecond());
        purgeOldMetrics(truncatedNow);
    }

    /**
     * 최근 24시간 내의 지표 목록 조회.
     */
    public List<SystemMetric> getRecentMetrics() {
        long minScore = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS).getEpochSecond();
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
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getCpuLoad();
            return load < 0 ? -1.0 : load * 100.0;
        }
        log.warn("com.sun.management.OperatingSystemMXBean not available on this JVM");
        return -1.0;
    }

    private long getMemoryUsed() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getMemoryMax() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    private void saveToRedis(SystemMetric metric, long score) {
        try {
            String json = objectMapper.writeValueAsString(metric);
            redisTemplate.execute(UPSERT_METRIC_SCRIPT,
                    List.of(METRIC_KEY),
                    String.valueOf(score), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize system metric", e);
        }
    }

    private void purgeOldMetrics(Instant now) {
        long maxScore = now.minus(RETENTION_HOURS, ChronoUnit.HOURS).getEpochSecond();
        redisTemplate.opsForZSet().removeRangeByScore(METRIC_KEY, 0, maxScore);
    }
}
