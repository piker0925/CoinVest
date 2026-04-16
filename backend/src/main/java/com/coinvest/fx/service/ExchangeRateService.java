package com.coinvest.fx.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.domain.ExchangeRate;
import com.coinvest.fx.repository.ExchangeRateRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.CircuitBreakerException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.AlertHistory;
import com.coinvest.portfolio.domain.AlertStatus;
import com.coinvest.portfolio.domain.AlertType;
import com.coinvest.portfolio.repository.AlertHistoryRepository;
import com.coinvest.portfolio.service.DiscordClient;
import com.coinvest.price.service.KisApiManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final DiscordClient discordClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AlertHistoryRepository alertHistoryRepository;
    private final KisApiManager kisApiManager;
    private final RedisTemplate<String, Object> redisTemplate;

    // 주말 갭 대응: 금요일 09:30 → 월요일 09:30 = 72h이므로 80h로 설정
    // KIS API는 영업일(MON-FRI)에만 호출됨. 주말 CircuitBreaker 오발화 방지.
    private static final int MAX_AGE_HOURS = 80;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final Map<String, ExchangeRate> localRateCache = new ConcurrentHashMap<>();

    @Value("${coinvest.alerts.system-webhook-url:}")
    private String systemWebhookUrl;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public ExchangeRateResponse getExchangeRateWithStatus(Currency base, Currency quote) {
        return getExchangeRateWithStatus(base, quote, PriceMode.LIVE);
    }

    /**
     * 환율 정보와 상태를 함께 반환.
     */
    public ExchangeRateResponse getExchangeRateWithStatus(Currency base, Currency quote, PriceMode mode) {
        if (base == quote) {
            return new ExchangeRateResponse(BigDecimal.ONE, false);
        }

        String pair = base.name() + ":" + quote.name();
        String redisKey = RedisKeyConstants.getExchangeRateKey(mode, pair);

        // 1. L1: Redis 확인
        Object redisVal = redisTemplate.opsForValue().get(redisKey);
        if (redisVal != null) {
            return new ExchangeRateResponse(new BigDecimal(redisVal.toString()), false);
        }

        // 2. DEMO 모드인데 Redis에 없으면 실패 (SimulatedFxProvider가 채워줘야 함)
        if (mode == PriceMode.DEMO) {
            log.error("Demo exchange rate not found in Redis: pair={}", pair);
            throw new CircuitBreakerException(ErrorCode.EXCHANGE_RATE_CIRCUIT_BREAKER_TRIGGERED);
        }

        // 3. LIVE 모드일 경우 L2(Local), L3(DB) 확인 및 Redis 갱신
        ExchangeRate rate = localRateCache.computeIfAbsent(pair, k -> 
            exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(base, quote)
                .orElse(null)
        );

        if (rate == null) {
            log.error("Exchange rate not found in DB: pair={}", pair);
            throw new CircuitBreakerException(ErrorCode.EXCHANGE_RATE_CIRCUIT_BREAKER_TRIGGERED);
        }

        BigDecimal rateVal = rate.getRate();
        redisTemplate.opsForValue().set(redisKey, rateVal.toString(), Duration.ofHours(1));

        boolean isStale = Duration.between(rate.getFetchedAt(), LocalDateTime.now()).toHours() > MAX_AGE_HOURS;
        return new ExchangeRateResponse(rateVal, isStale);
    }

    public record ExchangeRateResponse(BigDecimal rate, boolean isStale) {}

    @Transactional(readOnly = true)
    public BigDecimal getCurrentExchangeRate(Currency base, Currency quote) {
        return getCurrentExchangeRate(base, quote, PriceMode.LIVE);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentExchangeRate(Currency base, Currency quote, PriceMode mode) {
        ExchangeRateResponse response = getExchangeRateWithStatus(base, quote, mode);
        if (response.isStale()) {
            log.error("Strict exchange rate check failed due to staleness: base={}, quote={} (mode: {})", base, quote, mode);
            throw new CircuitBreakerException(ErrorCode.EXCHANGE_RATE_CIRCUIT_BREAKER_TRIGGERED);
        }
        return response.rate();
    }

    @Scheduled(cron = "0 30 9 * * MON-FRI")
    @Transactional
    public void fetchExchangeRate() {
        try {
            BigDecimal fetchedRate = fetchFromKisApi(); 
            
            ExchangeRate exchangeRate = ExchangeRate.builder()
                    .baseCurrency(Currency.USD)
                    .quoteCurrency(Currency.KRW)
                    .rate(fetchedRate)
                    .snapshotDate(LocalDateTime.now())
                    .fetchedAt(LocalDateTime.now())
                    .build();

            exchangeRateRepository.save(exchangeRate);
            localRateCache.put("USD:KRW", exchangeRate);
            
            // 실데이터 환율은 항상 LIVE 채널에 갱신
            String redisKey = RedisKeyConstants.getExchangeRateKey(PriceMode.LIVE, "USD:KRW");
            redisTemplate.opsForValue().set(redisKey, fetchedRate.toString(), Duration.ofHours(1));

            consecutiveFailures.set(0);
            log.info("Exchange rate (USD/KRW) fetched from KIS and cached in Redis (LIVE): {}", fetchedRate);

        } catch (Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            log.warn("Failed to fetch exchange rate from KIS (attempt {}): {}", failures, e.getMessage());

            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                String message = "🚨 KIS 환율 API 호출 5회 연속 실패. (Fallback 환율 사용 중) - " + e.getMessage();
                sendSystemAlert(message);
                recordFailureAlert("Exchange Rate Fetch Failed (KIS)", message);
            }
        }
    }

    private BigDecimal fetchFromKisApi() throws Exception {
        String token = kisApiManager.getAccessToken();
        if (token == null) throw new RuntimeException("Failed to get KIS access token for exchange rate");

        String uriStr = String.format("%s/uapi/overseas-stock/v1/quotations/inquire-daily-chartprice?FID_COND_MRKT_DIV_CODE=FX&FID_INPUT_ISCD=FX@USDKRW&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0",
                kisApiManager.getBaseUrl());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .header("authorization", "Bearer " + token)
                .header("appkey", kisApiManager.getAppKey())
                .header("appsecret", kisApiManager.getAppSecret())
                .header("tr_id", "FHKST03030100")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("KIS FX API returned status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode output = root.path("output1"); 
        
        if (output.isMissingNode() || output.isNull()) {
            JsonNode lastDay = root.path("output2").get(0);
            if (lastDay != null) {
                return new BigDecimal(lastDay.path("stck_clpr").asText());
            }
            throw new RuntimeException("Failed to parse FX rate from KIS response");
        }

        return new BigDecimal(output.path("ovrs_nmix_prpr").asText());
    }

    private void sendSystemAlert(String message) {
        if (systemWebhookUrl != null && !systemWebhookUrl.isBlank()) {
            try {
                discordClient.send(systemWebhookUrl, message);
            } catch (Exception e) {
                log.error("Failed to send system alert to Discord", e);
            }
        }
    }

    private void recordFailureAlert(String title, String details) {
        try {
            AlertHistory alert = AlertHistory.builder()
                    .type(AlertType.SYSTEM_ERROR)
                    .status(AlertStatus.FAILED)
                    .message(title + " : " + details)
                    .build();
            alertHistoryRepository.save(alert);
        } catch (Exception e) {
            log.error("Failed to save AlertHistory for ExchangeRateService", e);
        }
    }
}
