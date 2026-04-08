package com.coinvest.trading.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.Order;
import com.coinvest.trading.domain.Position;
import com.coinvest.trading.domain.Trade;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.dto.OrderResponse;
import com.coinvest.trading.dto.PositionResponse;
import com.coinvest.trading.dto.TradeResponse;
import com.coinvest.trading.dto.VirtualAccountResponse;
import com.coinvest.trading.repository.OrderRepository;
import com.coinvest.trading.repository.PositionRepository;
import com.coinvest.trading.repository.TradeRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingQueryService {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExchangeRateService exchangeRateService;

    public VirtualAccountResponse getAccount(Long userId) {
        VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        PriceMode mode = PriceModeResolver.resolve(account.getUser().getRole());

        // 1. 모든 통화 잔고를 KRW로 통합 환산 (Buying Power 통합)
        BigDecimal totalBalanceKrw = BigDecimal.ZERO;
        for (Balance balance : account.getBalances()) {
            BigDecimal rate = exchangeRateService.getExchangeRateWithStatus(balance.getCurrency(), Currency.KRW, mode).rate();
            BigDecimal balanceValueKrw = balance.getTotal().multiply(rate);
            totalBalanceKrw = totalBalanceKrw.add(balanceValueKrw);
        }

        List<PositionResponse> positions = getPositions(userId);
        BigDecimal totalPositionEvalKrw = positions.stream()
                .map(PositionResponse::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return VirtualAccountResponse.of(account, totalBalanceKrw, totalPositionEvalKrw);
    }

    public List<PositionResponse> getPositions(Long userId) {
        VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        PriceMode mode = PriceModeResolver.resolve(account.getUser().getRole());
        
        List<Position> positions = positionRepository.findAllByUserId(userId);

        return positions.stream()
                .map(pos -> {
                    BigDecimal currentPrice = getCurrentPriceFromRedis(pos.getUniversalCode(), mode);
                    return PositionResponse.of(pos, currentPrice);
                })
                .collect(Collectors.toList());
    }

    public CursorPageResponse<OrderResponse> getOrders(Long userId, Long cursorId, int size) {
        Pageable pageable = PageRequest.of(0, size);
        Slice<Order> orders;

        if (cursorId == null) {
            orders = orderRepository.findByUserIdOrderByIdDesc(userId, pageable);
        } else {
            orders = orderRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursorId, pageable);
        }

        List<OrderResponse> content = orders.getContent().stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());

        Long nextCursor = content.isEmpty() ? null : content.get(content.size() - 1).id();

        return new CursorPageResponse<>(content, nextCursor, orders.hasNext());
    }

    public CursorPageResponse<TradeResponse> getTrades(Long userId, Long cursorId, int size) {
        Pageable pageable = PageRequest.of(0, size);
        Slice<Trade> trades;

        if (cursorId == null) {
            trades = tradeRepository.findByUserIdOrderByIdDesc(userId, pageable);
        } else {
            trades = tradeRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursorId, pageable);
        }

        List<TradeResponse> content = trades.getContent().stream()
                .map(TradeResponse::from)
                .collect(Collectors.toList());

        Long nextCursor = content.isEmpty() ? null : content.get(content.size() - 1).id();

        return new CursorPageResponse<>(content, nextCursor, trades.hasNext());
    }

    private BigDecimal getCurrentPriceFromRedis(String universalCode, PriceMode mode) {
        String tickerKey = RedisKeyConstants.getTickerPriceKey(mode, universalCode);
        Object priceObj = redisTemplate.opsForValue().get(tickerKey);

        if (priceObj == null) return null;

        try {
            return new BigDecimal(priceObj.toString());
        } catch (NumberFormatException e) {
            log.error("Invalid price format in Redis for market: {} (mode: {})", universalCode, mode);
            return null;
        }
    }
}
