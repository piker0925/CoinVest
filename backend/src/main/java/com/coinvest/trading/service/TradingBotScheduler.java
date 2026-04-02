package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.auth.domain.UserRole;
import com.coinvest.trading.domain.OrderSide;
import com.coinvest.trading.domain.OrderType;
import com.coinvest.trading.dto.OrderCreateRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.bot.enabled", havingValue = "true")
public class TradingBotScheduler {

    private final UserRepository userRepository;
    private final TradingService tradingService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private final List<Long> cachedBotUserIds = new ArrayList<>();
    private final Random random = new Random();
    
    private static final List<String> TARGET_MARKETS = Arrays.asList("KRW-BTC", "KRW-ETH", "KRW-XRP");
    private static final int HOURLY_LIMIT = 100;

    /**
     * 무의미한 DB I/O 방지를 위해 기동 시 1회만 봇 계정 목록 캐싱
     */
    @PostConstruct
    public void initBotUsers() {
        List<User> botUsers = userRepository.findByRole(UserRole.USER).stream()
                .filter(u -> u.getEmail() != null && u.getEmail().startsWith("bot_"))
                .collect(Collectors.toList());
                
        for (User bot : botUsers) {
            cachedBotUserIds.add(bot.getId());
        }
        
        log.info("TradingBotScheduler initialized. Cached {} bot users.", cachedBotUserIds.size());
    }

    @Scheduled(fixedDelay = 300000) // 5분
    public void executeBotTrades() {
        if (cachedBotUserIds.isEmpty()) {
            return;
        }

        String currentHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

        for (Long botId : cachedBotUserIds) {
            String limitKey = "bot:order:count:" + botId + ":" + currentHour;
            Long currentCount = redisTemplate.opsForValue().increment(limitKey);
            
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(limitKey, 2, TimeUnit.HOURS);
            }
            
            if (currentCount != null && currentCount > HOURLY_LIMIT) {
                log.debug("Bot {} reached hourly limit", botId);
                continue;
            }

            try {
                executeRandomTrade(botId);
            } catch (Exception e) {
                log.warn("Bot trade failed for user {}: {}", botId, e.getMessage());
            }
        }
    }

    private void executeRandomTrade(Long botId) {
        String marketCode = TARGET_MARKETS.get(random.nextInt(TARGET_MARKETS.size()));
        OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        
        // 봇은 소액으로만 시장가 거래 (시뮬레이션 용도)
        BigDecimal quantity = new BigDecimal("0.005"); 
        
        OrderCreateRequest request = new OrderCreateRequest(
                marketCode,
                side,
                OrderType.MARKET,
                null,
                quantity
        );

        tradingService.createOrder(botId, request);
        log.info("Bot {} executed MARKET {} order for {}", botId, side, marketCode);
    }
}
