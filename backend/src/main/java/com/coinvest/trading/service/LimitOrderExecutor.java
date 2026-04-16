package com.coinvest.trading.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.util.BigDecimalUtil;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.TradeEvent;
import com.coinvest.trading.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 지정가 주문 개별 체결 트랜잭션 실행기.
 * LimitOrderMatchingService에서 분리된 별도 빈으로, Spring AOP 프록시를 통해
 * @Transactional이 정상 동작하도록 보장함.
 * (동일 클래스 내 자기 호출 시 프록시 미적용 문제 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimitOrderExecutor {

    private final OrderRepository orderRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final BalanceRepository balanceRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final SettlementRepository settlementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MarketHoursService marketHoursService;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");

    /**
     * 개별 매수 주문 체결 트랜잭션 (Short Transaction)
     *
     * @param tradeDate 체결 기준일 (KST). 정산일 계산의 기준이 됨.
     * @return 체결 완료 여부
     */
    @Transactional
    public boolean executeBuy(Long orderId, BigDecimal currentPrice, LocalDate tradeDate, Asset asset) {
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // Self-healing: 이미 체결된 주문
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        PriceMode mode = order.getPriceMode();
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();

        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(account.getId(), order.getCurrency())
                .orElseThrow(() -> new RuntimeException("Balance not found"));

        BigDecimal quantity = order.getQuantity();
        BigDecimal orderPrice = order.getPrice();

        BigDecimal lockedTotalAmount = orderPrice.multiply(quantity);
        BigDecimal lockedFee = BigDecimalUtil.formatKrw(lockedTotalAmount.multiply(FEE_RATE));
        BigDecimal lockedKrw = BigDecimalUtil.formatKrw(lockedTotalAmount.add(lockedFee));
        balance.unlock(lockedKrw);

        BigDecimal actualTotalAmount = currentPrice.multiply(quantity);
        BigDecimal actualFee = BigDecimalUtil.formatKrw(actualTotalAmount.multiply(FEE_RATE));
        BigDecimal actualKrw = BigDecimalUtil.formatKrw(actualTotalAmount.add(actualFee));
        balance.decreaseAvailable(actualKrw);

        Position position = positionRepository.findByUserIdAndUniversalCodeAndPriceMode(
                        order.getUser().getId(), order.getUniversalCode(), mode)
                .orElseGet(() -> Position.builder()
                        .user(order.getUser())
                        .universalCode(order.getUniversalCode())
                        .priceMode(mode)
                        .currency(order.getCurrency())
                        .avgBuyPrice(BigDecimal.ZERO)
                        .quantity(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .build());
        position.addPosition(currentPrice, quantity);
        positionRepository.save(position);

        LocalDate settlementDate = marketHoursService.calculateSettlementDate(asset, tradeDate);

        Trade trade = tradeRepository.save(Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .currency(order.getCurrency())
                .price(currentPrice)
                .quantity(quantity)
                .fee(actualFee)
                .realizedPnl(BigDecimal.ZERO)
                .priceMode(mode)
                .settlementDate(settlementDate)
                .build());

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(), order.getId(), order.getUser().getId(),
                order.getUniversalCode(), currentPrice, quantity,
                actualFee, trade.getRealizedPnl(), trade.getCreatedAt()));

        return true;
    }

    /**
     * 개별 매도 주문 체결 트랜잭션 (Short Transaction)
     *
     * @param tradeDate 체결 기준일 (KST). 정산일 계산의 기준이 됨.
     * @return 체결 완료 여부
     */
    @Transactional
    public boolean executeSell(Long orderId, BigDecimal currentPrice, LocalDate tradeDate, Asset asset) {
        int updated = orderRepository.updateStatusToFilledIfPending(orderId);
        if (updated == 0) {
            return true; // Self-healing: 이미 체결된 주문
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        PriceMode mode = order.getPriceMode();
        VirtualAccount account = virtualAccountRepository.findByUserId(order.getUser().getId()).orElseThrow();
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(account.getId(), order.getCurrency())
                .orElseThrow(() -> new RuntimeException("Balance not found"));

        Position position = positionRepository.findByUserIdAndUniversalCodeAndPriceMode(
                order.getUser().getId(), order.getUniversalCode(), mode).orElseThrow();

        BigDecimal quantity = order.getQuantity();
        position.unlockQuantity(quantity);

        BigDecimal totalAmount = currentPrice.multiply(quantity);
        BigDecimal fee = BigDecimalUtil.formatKrw(totalAmount.multiply(FEE_RATE));
        BigDecimal expectedReturn = BigDecimalUtil.formatKrw(totalAmount.subtract(fee));
        BigDecimal realizedPnl = currentPrice.subtract(position.getAvgBuyPrice()).multiply(quantity);
        position.subtractPosition(currentPrice, quantity);

        LocalDate settlementDate = marketHoursService.calculateSettlementDate(asset, tradeDate);
        if (asset.getAssetClass() == AssetClass.CRYPTO || asset.getAssetClass() == AssetClass.VIRTUAL) {
            balance.increaseAvailable(expectedReturn);
        } else {
            balance.increaseUnsettled(expectedReturn);
        }

        Trade trade = tradeRepository.save(Trade.builder()
                .order(order)
                .user(order.getUser())
                .universalCode(order.getUniversalCode())
                .currency(order.getCurrency())
                .price(currentPrice)
                .quantity(quantity)
                .fee(fee)
                .realizedPnl(BigDecimalUtil.formatKrw(realizedPnl))
                .priceMode(mode)
                .settlementDate(settlementDate)
                .build());

        if (asset.getAssetClass() != AssetClass.CRYPTO && asset.getAssetClass() != AssetClass.VIRTUAL) {
            settlementRepository.save(Settlement.builder()
                    .trade(trade)
                    .user(order.getUser())
                    .currency(order.getCurrency())
                    .amount(expectedReturn)
                    .settlementDate(settlementDate)
                    .status(Settlement.SettlementStatus.PENDING)
                    .priceMode(mode)
                    .build());
        }

        eventPublisher.publishEvent(new TradeEvent(
                trade.getId(), order.getId(), order.getUser().getId(),
                order.getUniversalCode(), currentPrice, quantity,
                fee, trade.getRealizedPnl(), trade.getCreatedAt()));

        return true;
    }
}
