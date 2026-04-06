package com.coinvest.fx.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.domain.ExchangeRate;
import com.coinvest.fx.repository.ExchangeRateRepository;
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

    private static final int MAX_AGE_HOURS = 48;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    @Value("${coinvest.alerts.system-webhook-url:}")
    private String systemWebhookUrl;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    @Transactional(readOnly = true)
    public BigDecimal getCurrentExchangeRate(Currency base, Currency quote) {
        if (base == quote) {
            return BigDecimal.ONE;
        }

        ExchangeRate rate = exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(base, quote)
                .orElseThrow(() -> new CircuitBreakerException(ErrorCode.EXCHANGE_RATE_CIRCUIT_BREAKER_TRIGGERED));

        if (Duration.between(rate.getFetchedAt(), LocalDateTime.now()).toHours() > MAX_AGE_HOURS) {
            log.error("Exchange rate stale (48h+): base={}, quote={}, last fetch={}", base, quote, rate.getFetchedAt());
            throw new CircuitBreakerException(ErrorCode.EXCHANGE_RATE_CIRCUIT_BREAKER_TRIGGERED);
        }

        return rate.getRate();
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
                    .snapshotDate(LocalDate.now())
                    .fetchedAt(LocalDateTime.now())
                    .build();

            exchangeRateRepository.save(exchangeRate);
            consecutiveFailures.set(0);
            log.info("Exchange rate (USD/KRW) fetched from KIS: {}", fetchedRate);

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

        // KIS 해외주식 환율 조회 TR: FHKST03030100
        // FID_COND_MRKT_DIV_CODE: FX, FID_INPUT_ISCD: FX@USDKRW (예시 규격)
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
        JsonNode output = root.path("output1"); // 실시간 환율 정보가 포함된 필드 (명세에 따라 다름)
        
        if (output.isMissingNode() || output.isNull()) {
            // 차트 API의 경우 output2 배열의 첫 번째 값 사용
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
