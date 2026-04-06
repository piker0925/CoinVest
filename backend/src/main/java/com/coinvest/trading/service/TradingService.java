package com.coinvest.trading.service;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.global.util.BigDecimalUtil;
import com.coinvest.price.service.PriceService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PriceService priceService;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");
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
            throw new ResourceNotFoundException(ErrorCode.TRADING_ORDER_NOT_FOUND);
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
            price = priceService.getCurrentPrice(request.universalCode());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
            }
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
            List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING);
            for (Order order : pendingOrders) {
                cancelOrder(userId, order.getId());
            }

            List<Position> positions = positionRepository.findByUserId(userId);
            for (Position position : positions) {
                if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                
                BigDecimal currentPrice = priceService.getCurrentPrice(position.getUniversalCode());
                if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    currentPrice = position.getAvgBuyPrice(); // Last fallback
                }
                executeMarketSell(position.getUser(), position.getUniversalCode(), currentPrice, position.getQuantity(), true);
            }

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
            redisTemplate.delete(cooldownKey);
            log.error("Failed to reset account for user {}, cooldown key deleted.", userId, e);
            throw e;
        }
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
            
            String redisKey = "trading:limit-order:buy:" + request.universalCode();
            redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), price.doubleValue());
            
        } else {
            Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), request.universalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));
            
            BigDecimal totalAmount = price.multiply(quantity);
            if (totalAmount.compareTo(MIN_ORDER_AMOUNT) < 0) {
                throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
            }

            position.lockQuantity(quantity);
            order = orderRepository.save(order);
            
            String redisKey = "trading:limit-order:sell:" + request.universalCode();
            redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), price.doubleValue());
        }

        return order.getId();
    }

    private void validateOrderRequest(OrderCreateRequest request) {
        if (request.type() == OrderType.MARKET && request.price() != null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }
        if (request.type() == OrderType.LIMIT && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException(ErrorCode.TRADING_INVALID_ORDER_PRICE);
        }
    }

    private Long processMarketOrder(User user, OrderCreateRequest request) {
        BigDecimal currentPrice = priceService.getCurrentPrice(request.universalCode());
        
        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        }
        
        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());

        if (request.side() == OrderSide.BUY) {
            return executeMarketBuy(user, request.universalCode(), currentPrice, quantity);
        } else {
            return executeMarketSell(user, request.universalCode(), currentPrice, quantity);
        }
    }

    private Long executeMarketBuy(User user, String universalCode, BigDecimal currentPrice, BigDecimal quantity) {
        VirtualAccount account = virtualAccountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal requiredKrw = BigDecimalUtil.formatKrw(totalAmount.add(fee));

        if (requiredKrw.compareTo(MIN_ORDER_AMOUNT) < 0) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }

        account.decreaseBalance(requiredKrw);

        Order order = Order.builder()
                .user(user)
                .universalCode(universalCode)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .price(null)
                .quantity(quantity)
                .status(OrderStatus.FILLED)
                .build();
        order.fill();
        order = orderRepository.save(order);

        Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), universalCode)
                .orElseGet(() -> Position.builder()
                        .user(user)
                        .universalCode(universalCode)
                        .avgBuyPrice(BigDecimal.ZERO)
                        .quantity(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .build());
        
        position.addPosition(currentPrice, quantity);
        positionRepository.save(position);

        Trade trade = Trade.builder()
                .order(order)
                .user(user)
                .universalCode(universalCode)
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimal.ZERO)
                .build();
        trade = tradeRepository.save(trade);

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(),
                order.getId(),
                user.getId(),
                universalCode,
                currentPrice,
                quantity,
                fee,
                trade.getRealizedPnl(),
                trade.getCreatedAt()
        ));

        return order.getId();
    }

    private Long executeMarketSell(User user, String universalCode, BigDecimal currentPrice, BigDecimal quantity) {
        return executeMarketSell(user, universalCode, currentPrice, quantity, false);
    }

    private Long executeMarketSell(User user, String universalCode, BigDecimal currentPrice, BigDecimal quantity, boolean isReset) {
        VirtualAccount account = virtualAccountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), universalCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedReturn = BigDecimalUtil.formatKrw(totalAmount.subtract(fee));

        if (!isReset && totalAmount.compareTo(MIN_ORDER_AMOUNT) < 0) {
             throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        }

        BigDecimal realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);

        position.subtractPosition(currentPrice, quantity);
        account.increaseBalance(expectedReturn);

        Order order = Order.builder()
                .user(user)
                .universalCode(universalCode)
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
                .universalCode(universalCode)
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimalUtil.formatKrw(realizedPnl))
                .build();
        tradeRepository.save(trade);

        return order.getId();
    }
}
