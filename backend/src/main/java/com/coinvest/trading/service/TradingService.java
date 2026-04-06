package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.global.util.BigDecimalUtil;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.*;
import com.coinvest.trading.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {

    private final UserRepository userRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final BalanceRepository balanceRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final SettlementRepository settlementRepository;
    private final AssetRepository assetRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final MarketHoursService marketHoursService;
    private final ApplicationEventPublisher eventPublisher;

    private final OrderValidator orderValidator;
    private final MarginCalculator marginCalculator;

    private static final BigDecimal MIN_ORDER_AMOUNT_KRW = new BigDecimal("5000");
    private static final BigDecimal MIN_ORDER_AMOUNT_USD = new BigDecimal("5");

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request) {
        orderValidator.validateRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        Asset asset = assetRepository.findByUniversalCode(request.universalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_ASSET_NOT_FOUND));

        BigDecimal currentPrice = getCurrentPrice(request, asset);
        boolean isMarketOpen = marketHoursService.isMarketOpen(asset);
        boolean isReservation = !isMarketOpen;

        List<Currency> currenciesToLock = Stream.of(Currency.KRW, Currency.USD)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toList());
        
        VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        List<Balance> balances = balanceRepository.findAllByAccountIdAndCurrenciesWithLock(account.getId(), currenciesToLock);
        Balance assetBalance = getBalance(balances, asset.getQuoteCurrency());
        Balance otherBalance = getBalance(balances, asset.getQuoteCurrency() == Currency.KRW ? Currency.USD : Currency.KRW);

        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());
        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
        BigDecimal requiredAmount = totalAmount.add(fee);

        orderValidator.validateMinOrderAmount(asset.getQuoteCurrency(), requiredAmount);

        BigDecimal fxRate = BigDecimal.ONE;
        if (request.side() == OrderSide.BUY) {
            fxRate = marginCalculator.calculateAndApplyMargin(assetBalance, otherBalance, requiredAmount);
        } else {
            Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), asset.getUniversalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));
            orderValidator.validateSellPosition(position, quantity);
            position.lockQuantity(quantity);
        }

        Order order = Order.builder()
                .user(user)
                .universalCode(asset.getUniversalCode())
                .currency(asset.getQuoteCurrency())
                .assetClass(asset.getAssetClass())
                .side(request.side())
                .type(request.type())
                .price(request.type() == OrderType.LIMIT ? request.price() : null)
                .quantity(quantity)
                .status(isReservation ? OrderStatus.PENDING : (request.type() == OrderType.MARKET ? OrderStatus.FILLED : OrderStatus.PENDING))
                .reservation(isReservation)
                .build();

        if (!isReservation && request.type() == OrderType.MARKET) {
            order.fill();
        }
        order = orderRepository.save(order);

        if (!isReservation && request.type() == OrderType.MARKET) {
            executeTrade(order, currentPrice, assetBalance, otherBalance, fxRate);
        } else if (request.type() == OrderType.LIMIT) {
            registerLimitOrder(order);
        }

        return order.getId();
    }

    @Transactional
    public void requestWithdrawal(Long userId, Currency currency, BigDecimal amount) {
        VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(account.getId(), currency)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_BALANCE_NOT_FOUND));

        if (balance.getAvailable().compareTo(amount) < 0) {
            if (balance.getAvailableForPurchase().compareTo(amount) >= 0) {
                throw new BusinessException(ErrorCode.WITHDRAWAL_RESTRICTION);
            }
            throw new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE);
        }

        balance.decreaseAvailable(amount);
        log.info("Withdrawal processed: [User={}, Amount={} {}]", userId, amount, currency);
    }

    @Transactional
    public void processReservedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!order.isReservation() || order.getStatus() != OrderStatus.PENDING) return;

        Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElseThrow();
        if (!marketHoursService.isMarketOpen(asset)) return;

        BigDecimal currentPrice = priceService.getCurrentPrice(order.getUniversalCode());
        
        List<Currency> currenciesToLock = Stream.of(Currency.KRW, Currency.USD)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toList());
        
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        List<Balance> balances = balanceRepository.findAllByAccountIdAndCurrenciesWithLock(account.getId(), currenciesToLock);
        Balance assetBalance = getBalance(balances, order.getCurrency());
        Balance otherBalance = getBalance(balances, order.getCurrency() == Currency.KRW ? Currency.USD : Currency.KRW);

        order.triggerReservation();
        order.fill();
        executeTrade(order, currentPrice, assetBalance, otherBalance, exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW));
    }

    public OrderPreviewResponse previewOrder(OrderPreviewRequest request) {
        Asset asset = assetRepository.findByUniversalCode(request.universalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_ASSET_NOT_FOUND));

        BigDecimal price = request.type() == OrderType.MARKET ? 
                priceService.getCurrentPrice(request.universalCode()) : request.price();
        
        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());
        BigDecimal totalAmount = price.multiply(quantity);
        BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
        BigDecimal requiredAmount = totalAmount.add(fee);

        boolean isReservation = !marketHoursService.isMarketOpen(asset);
        
        return new OrderPreviewResponse(
                price, quantity, fee, requiredAmount,
                isReservation, BigDecimal.ZERO, null
        );
    }

    private void executeTrade(Order order, BigDecimal currentPrice, Balance assetBalance, Balance otherBalance, BigDecimal fxRate) {
        Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElseThrow();
        BigDecimal totalAmount = currentPrice.multiply(order.getQuantity());
        BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
        BigDecimal realizedPnl = BigDecimal.ZERO;
        LocalDate settlementDate = marketHoursService.calculateSettlementDate(asset, LocalDate.now());

        if (order.getSide() == OrderSide.BUY) {
            BigDecimal actualRequired = totalAmount.add(fee);
            assetBalance.unlock(actualRequired);
            assetBalance.decreaseAvailable(actualRequired);
            
            Position position = positionRepository.findByUserIdAndUniversalCode(order.getUser().getId(), order.getUniversalCode())
                    .orElseGet(() -> Position.builder()
                            .user(order.getUser())
                            .universalCode(order.getUniversalCode())
                            .avgBuyPrice(BigDecimal.ZERO)
                            .quantity(BigDecimal.ZERO)
                            .realizedPnl(BigDecimal.ZERO)
                            .build());
            position.addPosition(currentPrice, order.getQuantity());
            positionRepository.save(position);
        } else {
            Position position = positionRepository.findByUserIdAndUniversalCode(order.getUser().getId(), order.getUniversalCode()).orElseThrow();
            realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(order.getQuantity());
            position.unlockQuantity(order.getQuantity());
            position.subtractPosition(currentPrice, order.getQuantity());
        }

        // Trade를 먼저 저장하여 ID를 확보 (무결성 보장)
        Trade trade = Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .currency(order.getCurrency())
                .exchangeRateSnapshot(fxRate)
                .price(currentPrice)
                .quantity(order.getQuantity())
                .fee(fee)
                .realizedPnl(order.getSide() == OrderSide.SELL ? realizedPnl : BigDecimal.ZERO)
                .settlementDate(settlementDate)
                .build();
        trade = tradeRepository.save(trade);

        // 매도 시에만 정산 데이터 생성
        if (order.getSide() == OrderSide.SELL) {
            BigDecimal netProceeds = totalAmount.subtract(fee);
            if (asset.getAssetClass() == AssetClass.CRYPTO) {
                assetBalance.increaseAvailable(netProceeds);
            } else {
                assetBalance.increaseUnsettled(netProceeds);
                Settlement settlement = Settlement.builder()
                        .trade(trade) // 저장된 trade 객체 바인딩
                        .user(order.getUser())
                        .currency(order.getCurrency())
                        .amount(netProceeds)
                        .settlementDate(settlementDate)
                        .status(Settlement.SettlementStatus.PENDING)
                        .build();
                settlementRepository.save(settlement);
            }
        }

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(), order.getId(), order.getUser().getId(), order.getUniversalCode(),
                currentPrice, order.getQuantity(), fee, trade.getRealizedPnl(), trade.getCreatedAt()
        ));
    }

    private void registerLimitOrder(Order order) {
        String side = order.getSide() == OrderSide.BUY ? "buy" : "sell";
        String redisKey = "trading:limit-order:" + side + ":" + order.getUniversalCode();
        redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), order.getPrice().doubleValue());
    }

    private BigDecimal getCurrentPrice(OrderCreateRequest request, Asset asset) {
        if (request.type() == OrderType.LIMIT) return request.price();
        BigDecimal price = priceService.getCurrentPrice(asset.getUniversalCode());
        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        return price;
    }

    private Balance getBalance(List<Balance> balances, Currency currency) {
        return balances.stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_BALANCE_NOT_FOUND));
    }

    @Transactional
    public void resetAccount(Long userId) {
        // Balance 기반 리셋 로직 추후 구현
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        // Balance 기반 취소 로직 추후 구현
    }
}
