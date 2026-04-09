package com.coinvest.price.service;

import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.trading.domain.MarketCalendar.Exchange;
import com.coinvest.trading.service.MarketHoursService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * LIVE 모드 전용 실제 벤치마크 지수 수집기.
 * KIS API로 KOSPI, S&P500 종가를 조회하여 Redis ZSet에 저장.
 * SimulatedBenchmark와 동일한 ZSet 패턴 사용 (BenchmarkService가 모드만 다르게 조회).
 *
 * <p><b>타임존/공휴일 방어 로직</b>:
 * 크론식의 요일(MON-FRI)만 믿지 않음.
 * 각 지수에 대해 MarketHoursService.wasOpenOn()으로
 * 실제 개장 여부를 먼저 확인한 후 API를 호출함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coinvest.live.enabled", matchIfMissing = false)
public class LiveBenchmarkProvider {

    private final KisApiManager kisApiManager;
    private final MarketHoursService marketHoursService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId ET  = ZoneId.of("America/New_York");

    // KIS API 지수 종목 코드
    private static final String KOSPI_CODE  = "0001"; // KOSPI 종합지수
    private static final String SP500_CODE  = "SPX";  // S&P 500

    /**
     * 매일 17:00 KST 실행 (KRX 종가 확정 후).
     * MON-FRI 크론식 + MarketHoursService Guard Clause 이중 방어.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Seoul")
    public void captureIndexPrices() {
        captureKospi();
        captureSp500();
    }

    // ─── KOSPI ───────────────────────────────────────────────────────────────

    private void captureKospi() {
        LocalDate today = LocalDate.now(KST);

        // Guard Clause: 오늘 KRX가 개장한 날인지 확인 (타임존/공휴일 방어)
        if (!marketHoursService.wasOpenOn(Exchange.KRX, today)) {
            log.debug("LiveBenchmarkProvider: Skipping KOSPI snapshot — KRX was not open on {}", today);
            return;
        }

        try {
            BigDecimal price = fetchKospiFromKis();
            saveToRedis("KOSPI", price, today);
            log.info("LiveBenchmarkProvider: KOSPI snapshot saved. date={}, value={}", today, price);
        } catch (Exception e) {
            log.warn("LiveBenchmarkProvider: Failed to fetch KOSPI index: {}", e.getMessage());
        }
    }

    // ─── S&P 500 ─────────────────────────────────────────────────────────────

    private void captureSp500() {
        // NYSE는 KST 기준 전날 종가 (17:00 KST = 03:00 ET — NYSE 개장 전이므로 전일 기준)
        LocalDate nyseDate = LocalDate.now(ET);

        // Guard Clause: 해당 날짜에 NYSE가 개장한 날인지 확인
        if (!marketHoursService.wasOpenOn(Exchange.NYSE, nyseDate)) {
            log.debug("LiveBenchmarkProvider: Skipping SP500 snapshot — NYSE was not open on {}", nyseDate);
            return;
        }

        try {
            BigDecimal price = fetchSp500FromKis();
            saveToRedis("SP500", price, nyseDate);
            log.info("LiveBenchmarkProvider: SP500 snapshot saved. date={}, value={}", nyseDate, price);
        } catch (Exception e) {
            log.warn("LiveBenchmarkProvider: Failed to fetch SP500 index: {}", e.getMessage());
        }
    }

    // ─── KIS API 호출 ─────────────────────────────────────────────────────────

    private BigDecimal fetchKospiFromKis() throws Exception {
        String token = kisApiManager.getAccessToken();
        String url = kisApiManager.getBaseUrl()
            + "/uapi/domestic-stock/v1/quotations/inquire-index-price?FID_COND_MRKT_DIV_CODE=U&FID_INPUT_ISCD="
            + KOSPI_CODE;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("authorization", "Bearer " + token)
            .header("appkey", kisApiManager.getAppKey())
            .header("appsecret", kisApiManager.getAppSecret())
            .header("tr_id", "FHPUP02100000")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("KIS API (KOSPI) returned status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String priceStr = root.path("output").path("bstp_nmix_prpr").asText();
        if (priceStr.isBlank()) {
            throw new RuntimeException("KIS API (KOSPI) response has empty price field");
        }
        return new BigDecimal(priceStr);
    }

    private BigDecimal fetchSp500FromKis() throws Exception {
        String token = kisApiManager.getAccessToken();
        String url = kisApiManager.getBaseUrl()
            + "/uapi/overseas-price/v1/quotations/inquire-price?AUTH=&EXCD=NYS&SYMB="
            + SP500_CODE;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("authorization", "Bearer " + token)
            .header("appkey", kisApiManager.getAppKey())
            .header("appsecret", kisApiManager.getAppSecret())
            .header("tr_id", "HHDFS00000300")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("KIS API (SP500) returned status: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String priceStr = root.path("output").path("last").asText();
        if (priceStr.isBlank()) {
            throw new RuntimeException("KIS API (SP500) response has empty price field");
        }
        return new BigDecimal(priceStr);
    }

    // ─── Redis ZSet 저장 ──────────────────────────────────────────────────────

    private void saveToRedis(String indexCode, BigDecimal price, LocalDate date) {
        String historyKey = RedisKeyConstants.getBenchmarkHistoryKey(PriceMode.LIVE, indexCode);

        long score = date.toEpochDay();
        // 동일 날짜 중복 저장 방지: remove → add (ZSet에서 score 기반 UPSERT)
        redisTemplate.opsForZSet().removeRangeByScore(historyKey, score, score);
        redisTemplate.opsForZSet().add(historyKey, price.toPlainString(), score);

        // 현재가 String 키도 갱신
        String priceKey = RedisKeyConstants.getBenchmarkKey(PriceMode.LIVE, indexCode);
        redisTemplate.opsForValue().set(priceKey, price.toPlainString(), Duration.ofHours(26));

        // 90일 초과 데이터 삭제
        long cutoff = date.minusDays(90).toEpochDay();
        redisTemplate.opsForZSet().removeRangeByScore(historyKey, 0, cutoff - 1);
    }
}
