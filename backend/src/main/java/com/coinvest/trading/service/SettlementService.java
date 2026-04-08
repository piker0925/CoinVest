package com.coinvest.trading.service;

import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.repository.SettlementRepository;
import com.coinvest.trading.service.strategy.TradingStrategy;
import com.coinvest.trading.service.strategy.TradingStrategyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final TradingStrategyResolver strategyResolver;

    /**
     * 개별 정산 처리 (독립적 트랜잭션 보장).
     * TradingStrategy를 통해 모드별(Live/Demo) 정산금 반영 로직을 수행함.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleSettlement(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow();

        if (settlement.getStatus() != Settlement.SettlementStatus.PENDING) {
            return;
        }

        // 1. 해당 정산 데이터의 모드에 맞는 전략 획득
        TradingStrategy strategy = strategyResolver.resolve(settlement.getPriceMode());

        try {
            // 2. 전략에 정산금 반영 위임
            strategy.settle(settlement);

            // 3. 정산 상태 업데이트 (성공)
            settlement.complete();
            log.info("Settlement Successful (mode: {}): [User={}, Amount={} {}, Date={}]", 
                    settlement.getPriceMode(), settlement.getUser().getId(), 
                    settlement.getAmount(), settlement.getCurrency(), settlement.getSettlementDate());
                    
        } catch (Exception e) {
            log.error("Settlement Failed (mode: {}): [ID={}, User={}]", 
                    settlement.getPriceMode(), settlement.getId(), settlement.getUser().getId(), e);
            settlement.fail();
            throw e; // 예외를 던져 트랜잭션 롤백 유도 (또는 상황에 따라 별도 처리)
        }
    }
}
