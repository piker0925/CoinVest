package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.KafkaTopicConstants;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.*;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import com.coinvest.portfolio.dto.PortfolioCreateRequest;
import com.coinvest.portfolio.dto.PortfolioResponse;
import com.coinvest.portfolio.dto.PortfolioUpdateRequest;
import com.coinvest.portfolio.dto.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 포트폴리오 비즈니스 로직 처리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AlertSettingRepository alertSettingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_PORTFOLIO_COUNT = 5;

    /**
     * 포트폴리오 생성.
     */
    @Transactional
    public PortfolioResponse createPortfolio(User user, PortfolioCreateRequest request) {
        // 1. 개수 제한 검증
        if (portfolioRepository.countByUser(user) >= MAX_PORTFOLIO_COUNT) {
            throw new BusinessException(ErrorCode.PORTFOLIO_LIMIT_EXCEEDED);
        }

        // 2. 비중 합 검증 (100% 여부)
        validateTotalWeight(request.getAssets());

        // 3. 엔티티 생성 및 저장
        Portfolio portfolio = Portfolio.builder()
                .name(request.getName())
                .initialInvestmentKrw(request.getInitialInvestmentKrw())
                .user(user)
                .build();

        request.getAssets().forEach(assetReq -> {
            PortfolioAsset asset = PortfolioAsset.builder()
                    .universalCode(assetReq.getUniversalCode())
                    .targetWeight(assetReq.getTargetWeight())
                    .quantity(BigDecimal.ZERO) // 초기 수량은 0
                    .build();
            portfolio.addAsset(asset);
        });

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        // 4. 기본 알림 설정 생성
        AlertSetting alertSetting = AlertSetting.builder()
                .portfolio(savedPortfolio)
                .deviationThreshold(new BigDecimal("0.0500")) // 기본 5%
                .isActive(true)
                .build();
        alertSettingRepository.save(alertSetting);

        // 5. Kafka 이벤트 발행
        publishPortfolioEvent(savedPortfolio, PortfolioUpdatedEvent.UpdateType.CREATE);

        return PortfolioResponse.from(savedPortfolio);
    }

    /**
     * 포트폴리오 목록 조회 (본인 것만).
     */
    public List<PortfolioResponse> getPortfolios(User user) {
        return portfolioRepository.findAllByUser(user).stream()
                .map(PortfolioResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 포트폴리오 상세 조회.
     */
    public PortfolioResponse getPortfolio(Long id, User user) {
        Portfolio portfolio = findByIdAndUser(id, user);
        return PortfolioResponse.from(portfolio);
    }

    /**
     * 포트폴리오 수정.
     */
    @Transactional
    public PortfolioResponse updatePortfolio(Long id, User user, PortfolioUpdateRequest request) {
        Portfolio portfolio = findByIdAndUser(id, user);

        // 1. 비중 합 검증
        validateTotalWeight(request.getAssets());

        // 2. 기본 정보 수정
        portfolio.update(request.getName(), portfolio.getInitialInvestmentKrw());

        // 3. 자산 교체 (Orphan Removal 기반)
        portfolio.getAssets().clear();
        request.getAssets().forEach(assetReq -> {
            PortfolioAsset asset = PortfolioAsset.builder()
                    .universalCode(assetReq.getUniversalCode())
                    .targetWeight(assetReq.getTargetWeight())
                    .quantity(BigDecimal.ZERO)
                    .build();
            portfolio.addAsset(asset);
        });

        // 4. Kafka 이벤트 발행
        publishPortfolioEvent(portfolio, PortfolioUpdatedEvent.UpdateType.UPDATE);

        return PortfolioResponse.from(portfolio);
    }

    /**
     * 포트폴리오 삭제.
     */
    @Transactional
    public void deletePortfolio(Long id, User user) {
        Portfolio portfolio = findByIdAndUser(id, user);
        portfolioRepository.delete(portfolio);

        publishPortfolioEvent(portfolio, PortfolioUpdatedEvent.UpdateType.DELETE);
    }

    /**
     * ID와 소유주 기반 포트폴리오 조회 (IDOR 방어).
     */
    private Portfolio findByIdAndUser(Long id, User user) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (!portfolio.getUser().getId().equals(user.getId())) {
            // 타인의 리소스 접근 시 존재하지 않는 것처럼 응답하여 보안 강화 (ADR 기록 예정)
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }
        return portfolio;
    }

    /**
     * 비중 합이 1.0(100%)인지 검증.
     */
    private void validateTotalWeight(List<PortfolioCreateRequest.AssetRequest> assets) {
        BigDecimal totalWeight = assets.stream()
                .map(PortfolioCreateRequest.AssetRequest::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // BigDecimal 비교 시 오차를 고려하지 않고 정확히 1.0000이어야 함 (0.0001 단위 정밀도)
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException(ErrorCode.PORTFOLIO_INVALID_WEIGHT);
        }
    }

    /**
     * Kafka 이벤트 발행.
     */
    private void publishPortfolioEvent(Portfolio portfolio, PortfolioUpdatedEvent.UpdateType type) {
        List<String> universalCodes = portfolio.getAssets().stream()
                .map(PortfolioAsset::getUniversalCode)
                .collect(Collectors.toList());

        PortfolioUpdatedEvent event = new PortfolioUpdatedEvent(
                portfolio.getId(),
                portfolio.getUser().getId(),
                universalCodes,
                type
        );

        kafkaTemplate.send(KafkaTopicConstants.PORTFOLIO_UPDATED, event);
        log.info("Published portfolio event: [id={}, type={}]", portfolio.getId(), type);
    }
}
