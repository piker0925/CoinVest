package com.coinvest.trading.service;

import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.global.exception.ResourceNotFoundException;
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
    private final TradeRepository tradeRepository;
    private final PositionRepository positionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // 테스트/초기자금 (Phase 3 요구사항에 1000만원 고정으로 산정할 수 있으나, 
    // 나중을 위해 Account 생성 시의 초기 자금을 기록하는 필드가 없다면 1000만원으로 하드코딩 또는 계좌 입금액을 조회해야 함. 
    // 임시로 1000만원 기준)
    private static final BigDecimal INITIAL_FUND = new BigDecimal("10000000");

    public CursorPageResponse<OrderResponse> getOrders(Long userId, Long cursorId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        Slice<Order> orderSlice;
        
        if (cursorId == null) {
            orderSlice = orderRepository.findByUserIdOrderByIdDesc(userId, pageRequest);
        } else {
            orderSlice = orderRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursorId, pageRequest);
        }

        List<OrderResponse> content = orderSlice.getContent().stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());

        Long nextCursor = null;
        if (orderSlice.hasNext() && !content.isEmpty()) {
            nextCursor = content.get(content.size() - 1).id();
        }

        return CursorPageResponse.of(content, nextCursor, orderSlice.hasNext());
    }

    public CursorPageResponse<TradeResponse> getTrades(Long userId, Long cursorId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        Slice<Trade> tradeSlice;
        
        if (cursorId == null) {
            tradeSlice = tradeRepository.findByUserIdOrderByIdDesc(userId, pageRequest);
        } else {
            tradeSlice = tradeRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursorId, pageRequest);
        }

        List<TradeResponse> content = tradeSlice.getContent().stream()
                .map(TradeResponse::from)
                .collect(Collectors.toList());

        Long nextCursor = null;
        if (tradeSlice.hasNext() && !content.isEmpty()) {
            nextCursor = content.get(content.size() - 1).id();
        }

        return CursorPageResponse.of(content, nextCursor, tradeSlice.hasNext());
    }

    public List<PositionResponse> getPositions(Long userId) {
        List<Position> positions = positionRepository.findByUserId(userId);
        
        return positions.stream()
                .map(pos -> {
                    BigDecimal currentPrice = getCurrentPriceFromRedis(pos.getUniversalCode());
                    if (currentPrice == null) {
                        // Redis 미스 시 평가를 위해 avgBuyPrice를 폴백으로 사용 (조회용이므로 예외 던지지 않음)
                        currentPrice = pos.getAvgBuyPrice(); 
                    }
                    return PositionResponse.of(pos, currentPrice);
                })
                .collect(Collectors.toList());
    }

    public VirtualAccountResponse getAccount(Long userId) {
        VirtualAccount account = virtualAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        List<PositionResponse> positions = getPositions(userId);
        
        return VirtualAccountResponse.of(account, positions, INITIAL_FUND);
    }

    private BigDecimal getCurrentPriceFromRedis(String universalCode) {
        String tickerKey = RedisKeyConstants.format(RedisKeyConstants.TICKER_PRICE_KEY, universalCode);
        Object priceObj = redisTemplate.opsForValue().get(tickerKey);
        if (priceObj == null) {
            return null;
        }
        try {
            return new BigDecimal(priceObj.toString());
        } catch (NumberFormatException e) {
            log.error("Invalid price format in Redis for market: {}", universalCode);
            return null;
        }
    }
}
