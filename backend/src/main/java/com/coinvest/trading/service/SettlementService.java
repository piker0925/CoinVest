package com.coinvest.trading.service;

import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.repository.BalanceRepository;
import com.coinvest.trading.repository.SettlementRepository;
import com.coinvest.trading.repository.VirtualAccountRepository;
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
    private final BalanceRepository balanceRepository;
    private final VirtualAccountRepository virtualAccountRepository;

    /**
     * 개별 정산 처리 (독립적 트랜잭션 보장)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleSettlement(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow();

        if (settlement.getStatus() != Settlement.SettlementStatus.PENDING) {
            return;
        }

        Long accountId = virtualAccountRepository.findByUserId(settlement.getUser().getId())
                .orElseThrow().getId();
        
        Balance balance = balanceRepository.findByAccountIdAndCurrencyWithLock(accountId, settlement.getCurrency())
                .orElseThrow();

        balance.settle(settlement.getAmount());
        settlement.settle();
        
        log.info("Settled: [User={}, Amount={} {}, Date={}]", 
                settlement.getUser().getId(), settlement.getAmount(), settlement.getCurrency(), settlement.getSettlementDate());
    }
}
