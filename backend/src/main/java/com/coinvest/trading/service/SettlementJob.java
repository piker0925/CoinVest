package com.coinvest.trading.service;

import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementJob {

    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;

    /**
     * 평일 오전 10:00에 정산 처리 (T+2)
     */
    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void runSettlement() {
        LocalDate today = LocalDate.now();
        log.info("Starting settlement job for date: {}", today);

        List<Settlement> pendingSettlements = settlementRepository.findAllByStatusAndSettlementDateBefore(
                Settlement.SettlementStatus.PENDING, today.plusDays(1));

        if (pendingSettlements.isEmpty()) {
            log.info("No pending settlements found.");
            return;
        }

        for (Settlement settlement : pendingSettlements) {
            try {
                // 건별 독립 트랜잭션으로 처리하여 하나가 실패해도 다른 건에 영향 없도록 함
                settlementService.processSingleSettlement(settlement.getId());
            } catch (Exception e) {
                log.error("Critical: Failed to process settlement ID: {}. Manual intervention required.", settlement.getId(), e);
                // 여기에 Discord 알림 등 관리자 통보 로직 추가 가능
            }
        }

        log.info("Settlement job finished. Processed {} items.", pendingSettlements.size());
    }
}
