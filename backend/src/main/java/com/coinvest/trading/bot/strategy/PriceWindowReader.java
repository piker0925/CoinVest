package com.coinvest.trading.bot.strategy;

import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis Price Window(5분봉) 조회 전용 리더.
 * PriceEventHandler에서 이미 5분 단위로 Time Bucketing된 
 * 종가(Close Price) 리스트를 단순 반환함 (CPU 연산 제로화).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceWindowReader {

    private static final int MAX_WINDOW_SIZE = 120;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 지정 자산의 5분봉 종가 목록을 오래된 순으로 정렬하여 반환 (SMA/RSI 계산용).
     * 데이터 부족 시 빈 리스트 반환 (전략 스킵 처리됨).
     */
    public List<BigDecimal> getCandles(String universalCode, PriceMode mode) {
        String key = RedisKeyConstants.getPriceWindowKey(mode, universalCode);
        
        // Redis 리스트는 LPUSH로 저장되므로 index 0이 가장 최신 캔들임.
        List<Object> raw = redisTemplate.opsForList().range(key, 0, MAX_WINDOW_SIZE - 1);
        
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<BigDecimal> candles = new ArrayList<>();
        
        // 전략(SMA, RSI) 공식들은 과거 데이터가 배열 앞쪽(index 0)에 오길 기대하므로,
        // 가져온 리스트(최신순)를 뒤집어서 오래된 순(Oldest first)으로 반환함.
        for (int i = raw.size() - 1; i >= 0; i--) {
            try {
                candles.add(new BigDecimal(raw.get(i).toString()));
            } catch (Exception e) {
                log.debug("Skipping malformed price window entry: {}", raw.get(i));
            }
        }

        return candles;
    }
}
