package com.coinvest.portfolio.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.global.common.PriceMode;
import com.coinvest.global.common.PriceModeResolver;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.*;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioCreateRequest;
import com.coinvest.portfolio.dto.PortfolioResponse;
import com.coinvest.portfolio.dto.PortfolioUpdateRequest;
import com.coinvest.portfolio.dto.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_PORTFOLIO_COUNT = 5;

    /**
     * 포트폴리오 생성.
     */
    @Transactional
    public PortfolioResponse createPortfolio(Long userId, PortfolioCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 1. 개수 제한 검증
        if (portfolioRepository.countByUser(user) >= MAX_PORTFOLIO_COUNT) {
            throw new BusinessException(ErrorCode.PORTFOLIO_LIMIT_EXCEEDED);
        }

        // 2. 비중 합 검증 (100% 여부)
        validateTotalWeight(request.getAssets());

        // 3. 엔티티 생성 및 저장 (유저 권한에 따른 PriceMode 할당)
        PriceMode mode = PriceModeResolver.resolve(user.getRole());

        Portfolio portfolio = Portfolio.builder()
                .name(request.getName())
                .initialInvestment(request.getInitialInvestment())
                .netContribution(request.getInitialInvestment())
                .baseCurrency(request.getBaseCurrency())
                .user(user)
                .priceMode(mode)
                .build();

        request.getAssets().forEach(assetReq -> {
            Asset assetMaster = assetRepository.findByUniversalCode(assetReq.getUniversalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));

            PortfolioAsset asset = PortfolioAsset.builder()
                    .universalCode(assetReq.getUniversalCode())
                    .targetWeight(assetReq.getTargetWeight())
                    .currency(assetMaster.getQuoteCurrency())
                    .quantity(BigDecimal.ZERO)
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

        // 5. 이벤트 발행
        publishPortfolioEvent(savedPortfolio, PortfolioUpdatedEvent.UpdateType.CREATE);

        return PortfolioResponse.from(savedPortfolio);
    }

    /**
     * 포트폴리오 목록 조회 (본인 것만).
     */
    public List<PortfolioResponse> getPortfolios(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return portfolioRepository.findAllByUser(user).stream()
                .map(PortfolioResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 포트폴리오 상세 조회.
     */
    public PortfolioResponse getPortfolio(Long id, Long userId) {
        Portfolio portfolio = findByIdAndUser(id, userId);
        return PortfolioResponse.from(portfolio);
    }

    /**
     * 포트폴리오 수정.
     */
    @Transactional
    public PortfolioResponse updatePortfolio(Long id, Long userId, PortfolioUpdateRequest request) {
        Portfolio portfolio = findByIdAndUser(id, userId);

        // 1. 비중 합 검증
        validateTotalWeight(request.getAssets());

        // 2. 기본 정보 수정
        portfolio.update(request.getName(), portfolio.getInitialInvestment(), portfolio.getBaseCurrency());

        // 3. 자산 교체 (Orphan Removal 기반)
        portfolio.getAssets().clear();
        request.getAssets().forEach(assetReq -> {
            Asset assetMaster = assetRepository.findByUniversalCode(assetReq.getUniversalCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));

            PortfolioAsset asset = PortfolioAsset.builder()
                    .universalCode(assetReq.getUniversalCode())
                    .targetWeight(assetReq.getTargetWeight())
                    .currency(assetMaster.getQuoteCurrency())
                    .quantity(BigDecimal.ZERO)
                    .build();
            portfolio.addAsset(asset);
        });

        // 4. 이벤트 발행
        publishPortfolioEvent(portfolio, PortfolioUpdatedEvent.UpdateType.UPDATE);

        return PortfolioResponse.from(portfolio);
    }

    /**
     * 포트폴리오 삭제.
     */
    @Transactional
    public void deletePortfolio(Long id, Long userId) {
        Portfolio portfolio = findByIdAndUser(id, userId);
        portfolioRepository.delete(portfolio);

        publishPortfolioEvent(portfolio, PortfolioUpdatedEvent.UpdateType.DELETE);
    }

    /**
     * ID와 소유주 기반 포트폴리오 조회 (IDOR 방어).
     */
    private Portfolio findByIdAndUser(Long id, Long userId) {
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (!portfolio.getUser().getId().equals(userId)) {
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

        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException(ErrorCode.PORTFOLIO_INVALID_WEIGHT);
        }
    }

    /**
     * 포트폴리오 업데이트 이벤트 발행.
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

        eventPublisher.publishEvent(event);
        log.info("Published portfolio event: [id={}, type={}]", portfolio.getId(), type);
    }
}
