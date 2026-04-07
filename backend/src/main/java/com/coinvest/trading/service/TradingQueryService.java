package com.coinvest.trading.service;

import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
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

/**
 * 거래 관련 조회 전용 서비스.
 */
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

    /**
     * 사용자의 가상 계좌 및 통합 자산 정보 조회.
     */
    public VirtualAccountResponse getAccount(Long userId) {
        VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 1. 모든 통화 잔고를 KRW로 통합 환산 (Buying Power 통합)
        BigDecimal totalBalanceKrw = BigDecimal.ZERO;
        for (Balance balance : account.getBalances()) {
            BigDecimal rate = exchangeRateService.getExchangeRateWithStatus(balance.getCurrency(), Currency.KRW).rate();
            BigDecimal balanceValueKrw = balance.getTotal().multiply(rate);
            totalBalanceKrw = totalBalanceKrw.add(balanceValueKrw);
        }

        // 2. 모든 보유 자산 평가액 합산 (KRW 기준)
        List<PositionResponse> positions = getPositions(userId);
        BigDecimal totalPositionEvalKrw = positions.stream()
                .map(PositionResponse::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 3. 최종 순자산(Net Worth) 반환
        return VirtualAccountResponse.of(account, totalBalanceKrw, totalPositionEvalKrw);
    }

    /**
     * 보유 포지션 리스트 조회 (현재가 포함).
     */
    public List<PositionResponse> getPositions(Long userId) {
        List<Position> positions = positionRepository.findAllByUserId(userId);

        return positions.stream()
                .map(pos -> {
                    BigDecimal currentPrice = getCurrentPriceFromRedis(pos.getUniversalCode());
                    return PositionResponse.of(pos, currentPrice);
                })
                .collect(Collectors.toList());
    }

    /**
     * 주문 내역 조회 (무한 스크롤).
     */
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

    /**
     * 체결 내역 조회 (무한 스크롤).
     */
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

    private BigDecimal getCurrentPriceFromRedis(String universalCode) {
        String tickerKey = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, universalCode);
        Object priceObj = redisTemplate.opsForValue().get(tickerKey);

        if (priceObj == null) return null;

        try {
            return new BigDecimal(priceObj.toString());
        } catch (NumberFormatException e) {
            log.error("Invalid price format in Redis for market: {}", universalCode);
            return null;
        }
    }
}
