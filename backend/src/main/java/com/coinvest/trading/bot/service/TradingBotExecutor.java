package com.coinvest.trading.bot.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.trading.bot.domain.BotStatus;
import com.coinvest.trading.bot.domain.TradingBot;
import com.coinvest.trading.bot.dto.BotTradingContext;
import com.coinvest.trading.bot.dto.OrderDecision;
import com.coinvest.trading.bot.repository.TradingBotRepository;
import com.coinvest.trading.bot.strategy.BotStrategyResolver;
import com.coinvest.trading.bot.strategy.BotTradingStrategy;
import com.coinvest.trading.domain.OrderType;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.service.MarketHoursService;
import com.coinvest.trading.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 봇 전략 실행기.
 *
 * - 5분 주기로 ACTIVE 봇을 실행
 * - Shared Context: 자산별 Redis Window를 한 번만 읽어 모든 봇에 공유 (성능 최적화)
 * - Jitter: 실행 시 랜덤 딜레이(0~10초)를 주어 부하 분산
 * - Rate limit: 봇당 시간당 50건 (Redis 카운터)
 * - MarketHours 존중: 장중에만 주문 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.bot.enabled", havingValue = "true")
public class TradingBotExecutor {

    private final TradingBotRepository botRepository;
    private final BotStrategyResolver strategyResolver;
    private final TradingService tradingService;
    private final MarketHoursService marketHoursService;
    private final AssetRepository assetRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int HOURLY_LIMIT = 50;
    private final Random random = new Random();

    @Scheduled(fixedDelay = 300_000)
    public void execute() {
        // Jitter 도입 (0~10초 랜덤 딜레이)
        try {
            TimeUnit.SECONDS.sleep(random.nextInt(11));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        List<TradingBot> activeBots = botRepository.findActiveBots(BotStatus.ACTIVE);
        if (activeBots.isEmpty()) return;

        // Shared Context: 모든 봇의 대상 자산 취합 및 Redis Window 한 번에 로드
        Map<String, List<BigDecimal>> sharedPriceWindows = loadSharedPriceWindows(activeBots);

        for (TradingBot bot : activeBots) {
            try {
                if (isRateLimited(bot.getId())) {
                    log.debug("Bot {} rate-limited", bot.getId());
                    continue;
                }

                BotTradingStrategy strategy = strategyResolver.resolve(bot.getStrategyType());
                BotTradingContext context = buildContext(bot, sharedPriceWindows);
                List<OrderDecision> decisions = strategy.decide(context);

                for (OrderDecision decision : decisions) {
                    Asset asset = assetRepository.findByUniversalCode(decision.universalCode())
                            .orElse(null);
                    if (asset == null) continue;
                    if (!marketHoursService.isMarketOpen(asset)) continue;

                    tradingService.createOrder(
                            bot.getUserId(),
                            new OrderCreateRequest(
                                    decision.universalCode(),
                                    decision.side(),
                                    OrderType.MARKET,
                                    null,
                                    decision.quantity()
                            ),
                            bot.getPriceMode()
                    );
                    incrementOrderCount(bot.getId());
                    log.info("Bot[{}] {} {} {} | {}",
                            bot.getStrategyType(), bot.getId(),
                            decision.side(), decision.universalCode(), decision.reason());
                }

            } catch (Exception e) {
                log.warn("Bot {} ({}) execution failed: {}", bot.getId(), bot.getStrategyType(), e.getMessage());
            }
        }
    }

    private Map<String, List<BigDecimal>> loadSharedPriceWindows(List<TradingBot> bots) {
        Set<String> allTargetAssets = bots.stream()
                .map(TradingBot::getConfig)
                .filter(Objects::nonNull)
                .map(config -> (List<?>) config.get("targetAssets"))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(Object::toString)
                .collect(Collectors.toSet());

        Map<String, List<BigDecimal>> windows = new HashMap<>();
        for (String assetCode : allTargetAssets) {
            String key = "price:window:" + assetCode;
            List<Object> rawList = redisTemplate.opsForList().range(key, 0, 59);
            if (rawList != null) {
                List<BigDecimal> prices = rawList.stream()
                        .map(obj -> new BigDecimal(obj.toString()))
                        .toList();
                windows.put(assetCode, prices);
            }
        }
        return windows;
    }

    private BotTradingContext buildContext(TradingBot bot, Map<String, List<BigDecimal>> sharedPriceWindows) {
        // 실제 구현 시에는 VirtualAccount와 Position 정보도 포함해야 함 (Task 3에서 고도화)
        return new BotTradingContext(bot.getUserId(), null, Collections.emptyList(), sharedPriceWindows);
    }

    private boolean isRateLimited(Long botId) {
        String key = "bot:order:count:" + botId + ":"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return false;
        return Integer.parseInt(val.toString()) >= HOURLY_LIMIT;
    }

    private void incrementOrderCount(Long botId) {
        String key = "bot:order:count:" + botId + ":"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 2, TimeUnit.HOURS);
        }
    }
}
