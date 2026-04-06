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
import java.math.RoundingMode;
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

    private static final BigDecimal MIN_ORDER_AMOUNT_KRW = new BigDecimal("5000");
    private static final BigDecimal MIN_ORDER_AMOUNT_USD = new BigDecimal("5");

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request) {
        validateOrderRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        Asset asset = assetRepository.findByUniversalCode(request.universalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_INPUT));

        // 1. 가격 및 장 상태 사전 확인 (락 획득 전)
        BigDecimal currentPrice = getCurrentPrice(request, asset);
        boolean isMarketOpen = marketHoursService.isMarketOpen(asset);
        boolean isReservation = !isMarketOpen;

        // 2. 통합 증거금 계산 및 잔고 락 (PESSIMISTIC_WRITE)
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

        validateMinOrderAmount(asset.getQuoteCurrency(), requiredAmount);

        BigDecimal exchangeRateSnapshot = BigDecimal.ONE;
        if (request.side() == OrderSide.BUY) {
            exchangeRateSnapshot = handleIntegratedMarginBuy(assetBalance, otherBalance, requiredAmount, asset.getQuoteCurrency());
        } else {
            handleSellPositionLock(user, asset, quantity);
        }

        // 3. 주문 생성
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

        // 4. 즉시 체결 처리
        if (!isReservation && request.type() == OrderType.MARKET) {
            executeTrade(order, currentPrice, assetBalance, otherBalance, exchangeRateSnapshot);
        } else if (request.type() == OrderType.LIMIT) {
            registerLimitOrder(order);
        }

        return order.getId();
    }

    /**
     * 예약 주문 실제 실행 (ReservationOrderExecutor에서 호출)
     */
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

        // 예약 주문은 시장가로 체결
        order.triggerReservation();
        order.fill();
        executeTrade(order, currentPrice, assetBalance, otherBalance, exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW));
    }

    public OrderPreviewResponse previewOrder(OrderPreviewRequest request) {
        Asset asset = assetRepository.findByUniversalCode(request.universalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_INPUT));

        BigDecimal price = request.type() == OrderType.MARKET ? 
                priceService.getCurrentPrice(request.universalCode()) : request.price();
        
        BigDecimal quantity = BigDecimalUtil.formatCoin(request.quantity());
        BigDecimal totalAmount = price.multiply(quantity);
        BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
        BigDecimal requiredAmount = totalAmount.add(fee);

        // 통합 증거금 예상액 계산 (로그인 유저 기준이면 더 정확하나 여기선 일반 로직)
        boolean isReservation = !marketHoursService.isMarketOpen(asset);
        
        return new OrderPreviewResponse(
                price, quantity, fee, requiredAmount,
                isReservation, BigDecimal.ZERO, null // 상세 환전 정보는 유저 잔고 연동 필요
        );
    }

    private BigDecimal handleIntegratedMarginBuy(Balance assetBalance, Balance otherBalance, BigDecimal requiredAmount, Currency quoteCurrency) {
        BigDecimal availableTotal = assetBalance.getAvailableForPurchase();
        BigDecimal fxRate = exchangeRateService.getCurrentExchangeRate(Currency.USD, Currency.KRW);

        if (availableTotal.compareTo(requiredAmount) < 0) {
            BigDecimal shortage = requiredAmount.subtract(availableTotal);
            BigDecimal requiredOtherCurrency = (quoteCurrency == Currency.USD) ?
                    shortage.multiply(fxRate).setScale(0, RoundingMode.UP) :
                    shortage.divide(fxRate, 2, RoundingMode.UP);

            otherBalance.decreaseAvailable(requiredOtherCurrency);
            assetBalance.increaseAvailable(shortage);
            
            log.info("Integrated Margin Used: Converted {} {} to {} {}", requiredOtherCurrency, otherBalance.getCurrency(), shortage, quoteCurrency);
        }
        
        assetBalance.lock(requiredAmount);
        return fxRate;
    }

    private void handleSellPositionLock(User user, Asset asset, BigDecimal quantity) {
        Position position = positionRepository.findByUserIdAndUniversalCode(user.getId(), asset.getUniversalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));
        position.lockQuantity(quantity);
    }

    private void executeTrade(Order order, BigDecimal currentPrice, Balance assetBalance, Balance otherBalance, BigDecimal fxRate) {
        Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElseThrow();
        BigDecimal totalAmount = currentPrice.multiply(order.getQuantity());
        BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
        BigDecimal actualRequired = totalAmount.add(fee);
        BigDecimal realizedPnl = BigDecimal.ZERO;
        LocalDate settlementDate = marketHoursService.calculateSettlementDate(asset, LocalDate.now());

        if (order.getSide() == OrderSide.BUY) {
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
            
            BigDecimal netProceeds = totalAmount.subtract(fee);
            if (asset.getAssetClass() == AssetClass.CRYPTO) {
                assetBalance.increaseAvailable(netProceeds);
            } else {
                assetBalance.increaseUnsettled(netProceeds);
                Settlement settlement = Settlement.builder()
                        .trade(null) // 아래에서 Trade 생성 후 업데이트
                        .user(order.getUser())
                        .currency(order.getCurrency())
                        .amount(netProceeds)
                        .settlementDate(settlementDate)
                        .status(Settlement.SettlementStatus.PENDING)
                        .build();
                settlementRepository.save(settlement);
            }
        }

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

        // 정산 데이터에 Trade ID 연결 (가능한 경우)
        settlementRepository.findAllByStatusAndSettlementDate(Settlement.SettlementStatus.PENDING, settlementDate).stream()
                .filter(s -> s.getTrade() == null && s.getUser().getId().equals(order.getUser().getId()))
                .findFirst()
                .ifPresent(s -> {
                    // 실제 운영 환경에선 더 정확한 매핑 필요하나 현재는 단순화
                });

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
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_BALANCE));
    }

    private void validateMinOrderAmount(Currency currency, BigDecimal amount) {
        BigDecimal min = (currency == Currency.KRW) ? MIN_ORDER_AMOUNT_KRW : MIN_ORDER_AMOUNT_USD;
        if (amount.compareTo(min) < 0) throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
    }

    private void validateOrderRequest(OrderCreateRequest request) {
        if (request.type() == OrderType.MARKET && request.price() != null) throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
        if (request.type() == OrderType.LIMIT && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) throw new BusinessException(ErrorCode.TRADING_INVALID_ORDER_PRICE);
    }

    @Transactional
    public void resetAccount(Long userId) {
        // 추후 구현
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        // 추후 구현
    }
}
