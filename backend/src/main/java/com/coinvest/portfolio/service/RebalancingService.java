package com.coinvest.portfolio.service;
import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.*;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.dto.RebalancingProposal;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 리밸런싱 엔진 및 시뮬레이션 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RebalancingService {

    private final PortfolioRepository portfolioRepository;
    private final AlertSettingRepository alertSettingRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final PortfolioValuationService valuationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001"); // 0.01%
    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");  // 업비트 기준 0.05%

    private static final String ALERT_COOLDOWN_KEY = "alert:cooldown:%d";
    private static final String ALERT_DAILY_COUNT_KEY = "alert:daily-count:%d";
    private static final int MAX_DAILY_ALERTS = 20;

    /**
...
     * 편차 검사 및 알림 필요 여부 확인 후 이벤트 발행.
     */
    public void processAlertTrigger(PortfolioValuation valuation) {
        if (!checkAlertTrigger(valuation)) {
            return;
        }

        Long portfolioId = valuation.getPortfolioId();

        // 1. 디바운스 체크 (5분)
        String cooldownKey = String.format(ALERT_COOLDOWN_KEY, portfolioId);
        Boolean isOnCooldown = redisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(isOnCooldown)) {
            return;
        }

        // 2. 일일 상한 체크 (20회)
        String dailyCountKey = String.format(ALERT_DAILY_COUNT_KEY, portfolioId);
        String dailyCountStr = (String) redisTemplate.opsForValue().get(dailyCountKey);
        int dailyCount = dailyCountStr != null ? Integer.parseInt(dailyCountStr) : 0;

        if (dailyCount >= MAX_DAILY_ALERTS) {
            log.warn("Daily alert limit exceeded for portfolio: {}", portfolioId);
            return;
        }

        // 3. Kafka 이벤트 발행
        kafkaTemplate.send(KafkaTopicConstants.ALERT_REBALANCE_TRIGGERED, valuation);

        // 4. Redis 상태 업데이트
        redisTemplate.opsForValue().set(cooldownKey, "LOCKED", Duration.ofMinutes(5));

        if (dailyCount == 0) {
            // 첫 알림 시 만료시간을 자정까지로 설정 (간단히 24시간 부여)
            redisTemplate.opsForValue().set(dailyCountKey, "1", Duration.ofHours(24));
        } else {
            redisTemplate.opsForValue().increment(dailyCountKey);
        }

        log.info("Published rebalancing alert event for portfolio: [id={}, count={}]", portfolioId, dailyCount + 1);
    }

    /**
     * 편차 검사 및 알림 필요 여부 확인.
     */
    public List<RebalancingProposal> simulateRebalancing(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        
        VirtualAccount account = virtualAccountRepository.findByUserId(portfolio.getUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PortfolioValuation valuation = valuationService.evaluate(portfolioId);
        if (valuation == null) return new ArrayList<>();

        List<RebalancingProposal> proposals = new ArrayList<>();
        BigDecimal totalPositionValue = valuation.getTotalEvaluationKrw();

        for (PortfolioValuation.AssetValuation av : valuation.getAssetValuations()) {
            BigDecimal currentWeight = av.getCurrentWeight() != null ? av.getCurrentWeight() : BigDecimal.ZERO;
            BigDecimal targetWeight = av.getTargetWeight();
            BigDecimal deviation = currentWeight.subtract(targetWeight);

            RebalancingProposal.Action action = RebalancingProposal.Action.HOLD;
            BigDecimal proposedQuantity = BigDecimal.ZERO;

            // 편차가 허용 범위를 초과하는 경우
            if (deviation.abs().compareTo(TOLERANCE) > 0) {
                // 목표 평가액 = 총 포지션 가치 * 목표 비중
                BigDecimal targetEvaluationKrw = totalPositionValue.multiply(targetWeight);
                BigDecimal diffAmount = targetEvaluationKrw.subtract(av.getCurrentEvaluationKrw());

                if (diffAmount.compareTo(BigDecimal.ZERO) > 0) {
                    // 매수 필요
                    action = RebalancingProposal.Action.BUY;
                    proposedQuantity = calculateProposedBuyQuantity(diffAmount, av.getCurrentPrice(), account.getAvailableBalance());
                } else if (diffAmount.compareTo(BigDecimal.ZERO) < 0) {
                    // 매도 필요
                    action = RebalancingProposal.Action.SELL;
                    proposedQuantity = diffAmount.abs().divide(av.getCurrentPrice(), 8, RoundingMode.HALF_UP);
                    // 실제 보유 수량보다 많이 팔 수 없음 (정합성 보장)
                    proposedQuantity = proposedQuantity.min(av.getQuantity());
                }
            }

            proposals.add(RebalancingProposal.builder()
                    .universalCode(av.getUniversalCode())
                    .currentWeight(currentWeight)
                    .targetWeight(targetWeight)
                    .deviation(deviation)
                    .action(action)
                    .proposedQuantity(proposedQuantity)
                    .currentPrice(av.getCurrentPrice())
                    .build());
        }

        return proposals;
    }

    /**
     * 편차 검사 및 알림 필요 여부 확인.
     * @return 알림을 보내야 하는 자산이 있으면 true
     */
    public boolean checkAlertTrigger(PortfolioValuation valuation) {
        Optional<AlertSetting> settingOpt = alertSettingRepository.findByPortfolioId(valuation.getPortfolioId());
        if (settingOpt.isEmpty() || !settingOpt.get().isActive()) {
            return false;
        }

        AlertSetting setting = settingOpt.get();
        BigDecimal threshold = setting.getDeviationThreshold();

        for (PortfolioValuation.AssetValuation av : valuation.getAssetValuations()) {
            BigDecimal currentWeight = av.getCurrentWeight() != null ? av.getCurrentWeight() : BigDecimal.ZERO;
            BigDecimal deviation = currentWeight.subtract(av.getTargetWeight()).abs();

            if (deviation.compareTo(threshold) > 0) {
                log.info("Rebalancing alert triggered: [portfolioId={}, market={}, deviation={}, threshold={}]",
                        valuation.getPortfolioId(), av.getUniversalCode(), deviation, threshold);
                return true;
            }
        }

        return false;
    }

    /**
     * 가용 잔고를 고려한 매수 제안 수량 계산.
     */
    private BigDecimal calculateProposedBuyQuantity(BigDecimal diffAmount, BigDecimal currentPrice, BigDecimal availableKrw) {
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        // 1. 순수하게 비중을 맞추기 위해 필요한 수량
        BigDecimal requiredQty = diffAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);

        // 2. 가용 잔고로 살 수 있는 최대 수량 (수수료 포함)
        // Max Qty = Available / (Price * (1 + Fee))
        BigDecimal maxAffordableQty = availableKrw.divide(
                currentPrice.multiply(BigDecimal.ONE.add(FEE_RATE)), 8, RoundingMode.DOWN);

        return requiredQty.min(maxAffordableQty);
    }
}
