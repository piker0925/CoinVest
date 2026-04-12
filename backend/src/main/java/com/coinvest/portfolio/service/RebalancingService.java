package com.coinvest.portfolio.service;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.*;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.dto.RebalancingProposal;
import com.coinvest.portfolio.event.RebalanceAlertEvent;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final ExchangeRateService exchangeRateService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001"); // 0.01%
    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");  // 기본 0.05%

    private static final String ALERT_COOLDOWN_KEY = "alert:cooldown:%d";
    private static final String ALERT_DAILY_COUNT_KEY = "alert:daily-count:%d";
    private static final int MAX_DAILY_ALERTS = 20;

    /**
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

        // 3. 이벤트 발행
        eventPublisher.publishEvent(new RebalanceAlertEvent(valuation));

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
     * 리밸런싱 시뮬레이션 수행.
     */
    public List<RebalancingProposal> simulateRebalancing(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
        
        VirtualAccount account = virtualAccountRepository.findByUserId(portfolio.getUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PortfolioValuation valuation = valuationService.evaluate(portfolioId);
        if (valuation == null) return new ArrayList<>();

        List<RebalancingProposal> proposals = new ArrayList<>();
        BigDecimal totalEvaluationBase = valuation.getTotalEvaluationBase();

        // 총 Buying Power (기준 통화 환산)
        BigDecimal totalBuyingPowerBase = BigDecimal.ZERO;
        for (Balance balance : account.getBalances()) {
            // 시뮬레이션에서도 stale 환율 허용 (Policy 2-B)
            BigDecimal fxRate = exchangeRateService.getExchangeRateWithStatus(balance.getCurrency(), valuation.getBaseCurrency()).rate();
            totalBuyingPowerBase = totalBuyingPowerBase.add(balance.getAvailableForPurchase().multiply(fxRate));
        }

        for (PortfolioValuation.AssetValuation av : valuation.getAssetValuations()) {
            BigDecimal currentWeight = av.getCurrentWeight() != null ? av.getCurrentWeight() : BigDecimal.ZERO;
            BigDecimal targetWeight = av.getTargetWeight();
            BigDecimal deviation = currentWeight.subtract(targetWeight);

            RebalancingProposal.Action action = RebalancingProposal.Action.HOLD;
            BigDecimal proposedQuantity = BigDecimal.ZERO;

            // 편차가 허용 범위를 초과하는 경우
            if (deviation.abs().compareTo(TOLERANCE) > 0) {
                // 목표 평가액 (기준 통화) = 총 포트폴리오 가치 * 목표 비중
                BigDecimal targetEvaluationBase = totalEvaluationBase.multiply(targetWeight);
                BigDecimal diffAmountBase = targetEvaluationBase.subtract(av.getEvaluationBase());

                if (diffAmountBase.compareTo(BigDecimal.ZERO) > 0) {
                    // 매수 필요 (기준 통화 -> 자산 통화 환산 필요)
                    action = RebalancingProposal.Action.BUY;
                    proposedQuantity = calculateProposedBuyQuantity(
                            diffAmountBase, 
                            av.getCurrentPrice(), 
                            av.getFxRate(), 
                            totalBuyingPowerBase
                    );
                } else if (diffAmountBase.compareTo(BigDecimal.ZERO) < 0) {
                    // 매도 필요
                    action = RebalancingProposal.Action.SELL;
                    // 자산 수량 = 기준 통화 차이 / (자산 가격 * 환율)
                    BigDecimal priceInBase = av.getCurrentPrice().multiply(av.getFxRate());
                    proposedQuantity = diffAmountBase.abs().divide(priceInBase, 8, RoundingMode.HALF_UP);
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
     * 가용 잔고(Buying Power)를 고려한 매수 제안 수량 계산.
     * 모든 금액은 기준 통화(Base)로 환산하여 계산함.
     */
    private BigDecimal calculateProposedBuyQuantity(
            BigDecimal diffAmountBase, 
            BigDecimal currentPriceNative, 
            BigDecimal fxRate, 
            BigDecimal totalBuyingPowerBase) {
        
        if (currentPriceNative.compareTo(BigDecimal.ZERO) == 0 || fxRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 1. 비중을 맞추기 위해 필요한 수량 (Price in Base = Price Native * FxRate)
        BigDecimal priceInBase = currentPriceNative.multiply(fxRate);
        BigDecimal requiredQty = diffAmountBase.divide(priceInBase, 8, RoundingMode.HALF_UP);

        // 2. 통합 증거금(Buying Power)으로 살 수 있는 최대 수량 (수수료 포함)
        // Max Qty = TotalBuyingPowerBase / (PriceInBase * (1 + Fee))
        BigDecimal maxAffordableQty = totalBuyingPowerBase.divide(
                priceInBase.multiply(BigDecimal.ONE.add(FEE_RATE)), 8, RoundingMode.DOWN);

        return requiredQty.min(maxAffordableQty);
    }
}
