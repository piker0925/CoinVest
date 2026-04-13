package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.global.util.BigDecimalUtil;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.*;
import com.coinvest.trading.repository.*;
import com.coinvest.trading.service.strategy.TradingStrategy;
import com.coinvest.trading.service.strategy.TradingStrategyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import com.coinvest.trading.domain.OrderStatus;
import com.coinvest.trading.domain.OrderSide;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 거래 핵심 서비스.
 */
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
    private final PortfolioRepository portfolioRepository; // 추가
    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final MarketHoursService marketHoursService;
    private final ApplicationEventPublisher eventPublisher;

    private final OrderValidator orderValidator;
    private final MarginCalculator marginCalculator;
    private final TradingStrategyResolver strategyResolver;

    @Transactional
    public Long createOrder(Long userId, OrderCreateRequest request, PriceMode mode) {
        orderValidator.validateRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        TradingStrategy strategy = strategyResolver.resolve(mode);

        Asset asset = assetRepository.findByUniversalCode(request.universalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_ASSET_NOT_FOUND));

        BigDecimal currentPrice = getCurrentPrice(request, asset, strategy);
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
            fxRate = marginCalculator.calculateAndApplyMargin(assetBalance, otherBalance, requiredAmount, mode);
        } else {
            Position position = strategy.getPosition(user.getId(), asset.getUniversalCode())
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
                .priceMode(mode)
                .build();

        if (!isReservation && request.type() == OrderType.MARKET) {
            order.fill();
        }
        order = orderRepository.save(order);

        if (!isReservation && request.type() == OrderType.MARKET) {
            executeTrade(order, currentPrice, assetBalance, otherBalance, fxRate, strategy);
        } else if (request.type() == OrderType.LIMIT) {
            strategy.registerLimitOrder(order);
        }

        return order.getId();
    }

    /**
     * 출금 요청 및 순 기여 금액(net_contribution) 차감.
     */
    @Transactional
    public void requestWithdrawal(Long userId, Currency currency, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
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

        // 1. 잔고 차감
        balance.decreaseAvailable(amount);

        // 2. 해당 사용자의 모든 포트폴리오 순 기여 금액(원금)에서 차감 (안분 비율 계산)
        List<Portfolio> portfolios = portfolioRepository.findAllByUser(user);
        if (!portfolios.isEmpty()) {
            BigDecimal totalContribution = portfolios.stream()
                    .map(Portfolio::getNetContribution)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            for (Portfolio portfolio : portfolios) {
                // 원화 환산 비율에 따라 원금 차감 (출금액을 해당 포트폴리오의 기준 통화로 변환하여 차감해야 함)
                BigDecimal fxRate = exchangeRateService.getExchangeRateWithStatus(currency, portfolio.getBaseCurrency(), portfolio.getPriceMode()).rate();
                BigDecimal withdrawalAmountInBase = amount.multiply(fxRate);

                // 비율대로 차감 (단일 포트폴리오인 경우 전액 차감)
                if (totalContribution.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal ratio = portfolio.getNetContribution().divide(totalContribution, 4, RoundingMode.HALF_UP);
                    portfolio.updateContribution(withdrawalAmountInBase.multiply(ratio).negate());
                } else {
                    // 기여금이 0인 경우 n분의 1 차감
                    portfolio.updateContribution(withdrawalAmountInBase.divide(BigDecimal.valueOf(portfolios.size()), 4, RoundingMode.HALF_UP).negate());
                }
            }
        }

        log.info("Withdrawal processed and net_contribution updated: [User={}, Amount={} {}]", userId, amount, currency);
    }

    @Transactional
    public void processReservedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!order.isReservation() || order.getStatus() != OrderStatus.PENDING) return;

        Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElseThrow();
        if (!marketHoursService.isMarketOpen(asset)) return;

        TradingStrategy strategy = strategyResolver.resolve(order.getPriceMode());
        BigDecimal currentPrice = strategy.getCurrentPrice(order.getUniversalCode());
        
        List<Currency> currenciesToLock = Stream.of(Currency.KRW, Currency.USD)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toList());
        
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        List<Balance> balances = balanceRepository.findAllByAccountIdAndCurrenciesWithLock(account.getId(), currenciesToLock);
        Balance assetBalance = getBalance(balances, order.getCurrency());
        Balance otherBalance = getBalance(balances, order.getCurrency() == Currency.KRW ? Currency.USD : Currency.KRW);

        order.triggerReservation();
        order.fill();
        executeTrade(order, currentPrice, assetBalance, otherBalance, strategy.getExchangeRate(Currency.USD, Currency.KRW), strategy);
    }

    public OrderPreviewResponse previewOrder(OrderPreviewRequest request, PriceMode mode) {
        Asset asset = assetRepository.findByUniversalCode(request.universalCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_ASSET_NOT_FOUND));

        TradingStrategy strategy = strategyResolver.resolve(mode);
        BigDecimal price = request.type() == OrderType.MARKET ? 
                strategy.getCurrentPrice(request.universalCode()) : request.price();
        
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

    private void executeTrade(Order order, BigDecimal currentPrice, Balance assetBalance, Balance otherBalance, BigDecimal fxRate, TradingStrategy strategy) {
        Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode()).orElseThrow();
        BigDecimal totalAmount = currentPrice.multiply(order.getQuantity());
        BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
        BigDecimal realizedPnl = BigDecimal.ZERO;
        // KST 기준 체결일 사용: UTC 서버 환경에서 LocalDate.now()는 자정 경계 오류를 유발함
        LocalDate tradeDate = order.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        LocalDate settlementDate = marketHoursService.calculateSettlementDate(asset, tradeDate);

        if (order.getSide() == OrderSide.BUY) {
            BigDecimal actualRequired = totalAmount.add(fee);
            assetBalance.unlock(actualRequired);
            assetBalance.decreaseAvailable(actualRequired);
            
            Position position = strategy.getPosition(order.getUser().getId(), order.getUniversalCode())
                    .orElseGet(() -> Position.builder()
                            .user(order.getUser())
                            .universalCode(order.getUniversalCode())
                            .currency(order.getCurrency())
                            .priceMode(strategy.getMode())
                            .avgBuyPrice(BigDecimal.ZERO)
                            .quantity(BigDecimal.ZERO)
                            .realizedPnl(BigDecimal.ZERO)
                            .build());
            position.addPosition(currentPrice, order.getQuantity());
            positionRepository.save(position);
        } else {
            Position position = strategy.getPosition(order.getUser().getId(), order.getUniversalCode()).orElseThrow();
            realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(order.getQuantity());
            position.unlockQuantity(order.getQuantity());
            position.subtractPosition(currentPrice, order.getQuantity());
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
                .priceMode(strategy.getMode())
                .settlementDate(settlementDate)
                .build();
        trade = tradeRepository.save(trade);

        if (order.getSide() == OrderSide.SELL) {
            BigDecimal netProceeds = totalAmount.subtract(fee);
            if (asset.getAssetClass() == AssetClass.CRYPTO) {
                assetBalance.increaseAvailable(netProceeds);
            } else {
                assetBalance.increaseUnsettled(netProceeds);
                Settlement settlement = Settlement.builder()
                        .trade(trade)
                        .user(order.getUser())
                        .currency(order.getCurrency())
                        .amount(netProceeds)
                        .settlementDate(settlementDate)
                        .status(Settlement.SettlementStatus.PENDING)
                        .priceMode(strategy.getMode())
                        .build();
                settlementRepository.save(settlement);
            }
        }

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(), order.getId(), order.getUser().getId(), order.getUniversalCode(),
                currentPrice, order.getQuantity(), fee, trade.getRealizedPnl(), trade.getCreatedAt()
        ));
    }

    private BigDecimal getCurrentPrice(OrderCreateRequest request, Asset asset, TradingStrategy strategy) {
        if (request.type() == OrderType.LIMIT) return request.price();
        BigDecimal price = strategy.getCurrentPrice(asset.getUniversalCode());
        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
        return price;
    }

    private Balance getBalance(List<Balance> balances, Currency currency) {
        return balances.stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_BALANCE_NOT_FOUND));
    }

    private static final BigDecimal INITIAL_KRW = new BigDecimal("100000000"); // 1억
    private static final BigDecimal INITIAL_USD = new BigDecimal("10000");     // 1만

    /**
     * 지정가 주문 취소.
     * BUY: 잠긴 잔고 해제 / SELL: 잠긴 수량 해제 / Redis ZSet에서 제거.
     */
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.TRADING_ORDER_NOT_FOUND); // IDOR 마스킹
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRADING_ORDER_NOT_CANCELABLE);
        }

        order.cancel();

        if (order.getSide() == OrderSide.BUY) {
            VirtualAccount account = virtualAccountRepository.findByUserId(userId).orElseThrow();
            Balance balance = balanceRepository
                    .findByAccountIdAndCurrencyWithLock(account.getId(), order.getCurrency())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_BALANCE_NOT_FOUND));
            Asset asset = assetRepository.findByUniversalCode(order.getUniversalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_ASSET_NOT_FOUND));
            // 잠금 시와 동일한 수식 사용 (formatKrw 없음 — 소수점 오차 방지)
            BigDecimal totalAmount = order.getPrice().multiply(order.getQuantity());
            BigDecimal fee = totalAmount.multiply(asset.getFeeRate());
            balance.unlock(totalAmount.add(fee));
        } else {
            TradingStrategy strategy = strategyResolver.resolve(order.getPriceMode());
            Position position = strategy.getPosition(userId, order.getUniversalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_INSUFFICIENT_QUANTITY));
            position.unlockQuantity(order.getQuantity());
        }

        // Redis 지정가 대기열에서 제거
        String side = order.getSide() == OrderSide.BUY ? "buy" : "sell";
        String key = RedisKeyConstants.getLimitOrderKey(order.getPriceMode(), side, order.getUniversalCode());
        redisTemplate.opsForZSet().remove(key, orderId.toString());
    }

    /**
     * DEMO 계좌 초기화.
     * 잔고 계산 없이 Brute-force로 상태를 초기값으로 되돌림.
     * 미체결 주문은 Redis 대기열에서 제거 후 CANCELLED 처리.
     * 개별 잠금 해제 루프를 피해 데드락 위험 없음.
     */
    @Transactional
    public void resetAccount(Long userId) {
        // 1. DEMO 미체결 주문 → Redis 대기열 제거 (잔고 계산 없이 상태만 변경)
        List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)
                .stream()
                .filter(o -> o.getPriceMode() == PriceMode.DEMO)
                .toList();

        for (Order order : pendingOrders) {
            String side = order.getSide() == OrderSide.BUY ? "buy" : "sell";
            String key = RedisKeyConstants.getLimitOrderKey(order.getPriceMode(), side, order.getUniversalCode());
            redisTemplate.opsForZSet().remove(key, order.getId().toString());
        }

        // 2. 고아 레코드 삭제: FK 의존 순서 준수 (Settlement → Trade → Order)
        settlementRepository.deleteAllByUserIdAndPriceMode(userId, PriceMode.DEMO);
        tradeRepository.deleteAllByUserIdAndPriceMode(userId, PriceMode.DEMO);
        orderRepository.deleteAllByUserIdAndPriceMode(userId, PriceMode.DEMO);

        // 3. DEMO 포지션 전부 삭제
        positionRepository.deleteAllByUserIdAndPriceMode(userId, PriceMode.DEMO);

        // 4. 잔고 초기화 (어차피 덮어씌우므로 개별 unlock 계산 불필요)
        VirtualAccount account = virtualAccountRepository.findByUserId(userId).orElseThrow();
        List<Balance> balances = balanceRepository.findAllByAccountId(account.getId());
        for (Balance balance : balances) {
            if (balance.getCurrency() == Currency.KRW) {
                balance.resetTo(INITIAL_KRW);
            } else if (balance.getCurrency() == Currency.USD) {
                balance.resetTo(INITIAL_USD);
            }
        }

        log.info("DEMO account reset for userId={}", userId);
    }
}
