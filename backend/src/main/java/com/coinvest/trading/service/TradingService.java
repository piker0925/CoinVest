package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.global.util.BigDecimalUtil;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.dto.OrderPreviewRequest;
import com.coinvest.trading.dto.OrderPreviewResponse;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.TradeRepository;
import com.coinvest.trading.dto.TradeEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.coinvest.trading.repository.VirtualAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {

    private final UserRepository userRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005"); // 0.05%
    private static final BigDecimal MIN_ORDER_AMOUNT = new BigDecimal("5000");
    private static final String RESET_COOLDOWN_KEY_PREFIX = "account:reset:cooldown:";
    private static final long RESET_COOLDOWN_HOURS = 24;

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request) {
        validateOrderRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (request.type() == OrderType.MARKET) {
            return processMarketOrder(user, request);
        } else {
            return processLimitOrder(user, request);
        }
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRADING_ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.TRADING_ORDER_NOT_FOUND); // IDOR
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRADING_ORDER_NOT_CANCELABLE);
        }

        order.cancel();

        if (order.getSide() == OrderSide.BUY) {
            VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
            BigDecimal totalAmount = order.getPrice().multiply(order.getQuantity());
            BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
            BigDecimal requiredKrw = BigDecimalUtil.formatKrw(totalAmount.add(fee));
            account.unlockBalance(requiredKrw);

            String redisKey = "trading:limit-order:buy:" + order.getUniversalCode();
            redisTemplate.opsForZSet().remove(redisKey, order.getId().toString());
        } else {
            Position position = positionRepository.findByUserIdAndUniversalCode(userId, order.getUniversalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));
            position.unlockQuantity(order.getQuantity());

            String redisKey = "trading:limit-order:sell:" + order.getUniversalCode();
            redisTemplate.opsForZSet().remove(redisKey, order.getId().toString());
        }
    }

    public OrderPreviewResponse previewOrder(OrderPreviewRequest request) {
        BigDecimal price;
        if (request.type() == OrderType.MARKET) {
            String tickerKey = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, request.universalCode());
            Object priceObj = redisTemplate.opsForValue().get(tickerKey);
            if (priceObj == null) {
                throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR); // 시세 조회 실패
            }
            price = new BigDecimal(priceObj.toString());
        } else {
            if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.TRADING_INVALID_ORDER_PRICE);
            }
            price = BigDecimalUtil.formatKrw(request.price());
        }

        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());
        BigDecimal totalAmount = price.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedTotalAmount = request.side() == OrderSide.BUY ? 
                BigDecimalUtil.formatKrw(totalAmount.add(fee)) : 
                BigDecimalUtil.formatKrw(totalAmount.subtract(fee));

        return new OrderPreviewResponse(price, quantity, fee, expectedTotalAmount);
    }

    @Transactional
    public void resetAccount(Long userId) {
        String cooldownKey = RESET_COOLDOWN_KEY_PREFIX + userId;
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", java.time.Duration.ofHours(RESET_COOLDOWN_HOURS));
        
        if (Boolean.FALSE.equals(isSet)) {
            throw new BusinessException(ErrorCode.COMMON_TOO_MANY_REQUESTS);
        }

        try {
            // 1. Cancel all PENDING orders
            List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING);
            for (Order order : pendingOrders) {
                cancelOrder(userId, order.getId());
            }

            // 2. Sell all positions at market price
            List<Position> positions = positionRepository.findByUserId(userId);
            RestTemplate restTemplate = new RestTemplate();

            for (Position position : positions) {
                if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                BigDecimal currentPrice = getCurrentPriceWithFallback(position, restTemplate);
                executeMarketSell(position.getUser(), position.getUniversalCode(), currentPrice, position.getQuantity(), true);
            }

            // 3. Reset balance to INITIAL_FUND
            VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
            
            if (account.getLockedKrw().compareTo(BigDecimal.ZERO) > 0) {
                account.unlockBalance(account.getLockedKrw());
            }
            
            BigDecimal initialFund = new BigDecimal("10000000");
            if (account.getBalanceKrw().compareTo(initialFund) < 0) {
                account.increaseBalance(initialFund.subtract(account.getBalanceKrw()));
            } else if (account.getBalanceKrw().compareTo(initialFund) > 0) {
                account.decreaseBalance(account.getBalanceKrw().subtract(initialFund));
            }
        } catch (Exception e) {
            // 보상 로직: 예외 발생 시 쿨다운 키를 즉시 삭제하여 유저가 갇히는 현상 방지
            redisTemplate.delete(cooldownKey);
            log.error("Failed to reset account for user {}, cooldown key deleted.", userId, e);
            throw e; // Exception Swallowing 방지 (정상적인 DB 롤백 유도)
        }
    }

    private BigDecimal getCurrentPriceWithFallback(Position position, RestTemplate restTemplate) {
        String tickerKey = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, position.getUniversalCode());
        Object priceObj = redisTemplate.opsForValue().get(tickerKey);
        if (priceObj != null) {
            try {
                return new BigDecimal(priceObj.toString());
            } catch (NumberFormatException ignored) {}
        }
        
        try {
            String url = "https://api.upbit.com/v1/ticker?markets=" + position.getUniversalCode();
            JsonNode[] response = restTemplate.getForObject(url, JsonNode[].class);
            if (response != null && response.length > 0) {
                return new BigDecimal(response[0].get("trade_price").asText());
            }
        } catch (Exception e) {
            log.warn("Upbit REST API fallback failed for market: {}", position.getUniversalCode(), e);
        }

        log.warn("Using avgBuyPrice as fallback for market: {}", position.getUniversalCode());
        return position.getAvgBuyPrice();
    }

    private Long processLimitOrder(User user, OrderCreateRequest request) {
        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());
        BigDecimal price = BigDecimalUtil.formatKrw(request.price());
        
        Order order = Order.builder()
                .user(user)
                .universalCode(request.universalCode())
                .side(request.side())
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(quantity)
                .status(OrderStatus.PENDING)
                .build();

        if (request.side() == OrderSide.BUY) {
            VirtualAccount account = virtualAccountRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

            BigDecimal totalAmount = price.multiply(quantity);
            BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
            BigDecimal requiredKrw = BigDecimalUtil.formatKrw(totalAmount.add(fee));

            if (requiredKrw.compareTo(MIN_ORDER_AMOUNT) < 0) {
                throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
            }

            account.lockBalance(requiredKrw);
            order = orderRepository.save(order);
            
            // Redis ZSet에 지정가 매수 주문 등록 (score = price)
            String redisKey = "trading:limit-order:buy:" + request.universalCode();
            redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), price.doubleValue());
            
        } else { // SELL
            Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), request.universalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));
            
            BigDecimal totalAmount = price.multiply(quantity);
            if (totalAmount.compareTo(MIN_ORDER_AMOUNT) < 0) {
                throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
            }

            position.lockQuantity(quantity);
            order = orderRepository.save(order);
            
            // Redis ZSet에 지정가 매도 주문 등록 (score = price)
            String redisKey = "trading:limit-order:sell:" + request.universalCode();
            redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), price.doubleValue());
        }

        return order.getId();
    }

    private void validateOrderRequest(OrderCreateRequest request) {
        if (request.type() == OrderType.MARKET && request.price() != null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT); // 가격 없어야 함
        }
        if (request.type() == OrderType.LIMIT && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException(ErrorCode.TRADING_INVALID_ORDER_PRICE);
        }
    }

    private Long processMarketOrder(User user, OrderCreateRequest request) {
        String tickerKey = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, request.universalCode());
        Object priceObj = redisTemplate.opsForValue().get(tickerKey);
        
        if (priceObj == null) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR); // 시세 조회 실패
        }
        
        BigDecimal currentPrice = new BigDecimal(priceObj.toString());
        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());

        if (request.side() == OrderSide.BUY) {
            return executeMarketBuy(user, request.universalCode(), currentPrice, quantity);
        } else {
            return executeMarketSell(user, request.universalCode(), currentPrice, quantity);
        }
    }

    private Long executeMarketBuy(User user, String marketCode, BigDecimal currentPrice, BigDecimal quantity) {
        VirtualAccount account = virtualAccountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND)); // 추후 가상계좌 없음 예외로 변경

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal requiredKrw = BigDecimalUtil.formatKrw(totalAmount.add(fee));

        if (requiredKrw.compareTo(MIN_ORDER_AMOUNT) < 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT); // 최소 주문 금액 미달
        }

        account.decreaseBalance(requiredKrw);

        Order order = Order.builder()
                .user(user)
                .universalCode(marketCode)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .price(null)
                .quantity(quantity)
                .status(OrderStatus.FILLED)
                .build();
        order.fill(); // status 및 filledAt 갱신
        order = orderRepository.save(order);

        Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), marketCode)
                .orElseGet(() -> Position.builder()
                        .user(user)
                        .universalCode(marketCode)
                        .avgBuyPrice(BigDecimal.ZERO)
                        .quantity(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .build());
        
        position.addPosition(currentPrice, quantity);
        position = positionRepository.save(position);

        Trade trade = Trade.builder()
                .order(order)
                .user(user)
                .universalCode(marketCode)
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimal.ZERO) // 매수 시 실현 손익 0
                .build();
        trade = tradeRepository.save(trade);

        // Publish event
        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(),
                order.getId(),
                user.getId(),
                marketCode,
                currentPrice,
                quantity,
                fee,
                trade.getRealizedPnl(),
                trade.getCreatedAt()
        ));

        return order.getId();
    }

    private Long executeMarketSell(User user, String marketCode, BigDecimal currentPrice, BigDecimal quantity) {
        return executeMarketSell(user, marketCode, currentPrice, quantity, false);
    }

    private Long executeMarketSell(User user, String marketCode, BigDecimal currentPrice, BigDecimal quantity, boolean isReset) {
        VirtualAccount account = virtualAccountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), marketCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedReturn = BigDecimalUtil.formatKrw(totalAmount.subtract(fee));

        if (!isReset && totalAmount.compareTo(MIN_ORDER_AMOUNT) < 0) {
             throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT); // 최소 주문 금액 미달
        }

        // 실현 손익 계산 전 현재 평단가 기록
        BigDecimal realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);

        position.subtractPosition(currentPrice, quantity); // 수량 검증 포함됨
        account.increaseBalance(expectedReturn);

        Order order = Order.builder()
                .user(user)
                .universalCode(marketCode)
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .price(null)
                .quantity(quantity)
                .status(OrderStatus.FILLED)
                .build();
        order.fill();
        order = orderRepository.save(order);

        Trade trade = Trade.builder()
                .order(order)
                .user(user)
                .universalCode(marketCode)
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimalUtil.formatKrw(realizedPnl))
                .build();
        tradeRepository.save(trade);

        return order.getId();
    }
}
