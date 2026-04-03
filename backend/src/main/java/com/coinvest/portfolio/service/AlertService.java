package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
import com.coinvest.global.common.CursorPageResponse;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.*;
import com.coinvest.portfolio.dto.AlertHistoryResponse;
import com.coinvest.portfolio.dto.AlertSettingResponse;
import com.coinvest.portfolio.dto.AlertSettingUpdateRequest;
import com.coinvest.portfolio.repository.AlertHistoryRepository;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 알림 설정 및 이력 비즈니스 로직 처리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    private final AlertSettingRepository alertSettingRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final PortfolioRepository portfolioRepository;

    /**
     * 알림 설정 조회.
     */
    public AlertSettingResponse getAlertSetting(Long portfolioId, User user) {
        Portfolio portfolio = validatePortfolioOwnership(portfolioId, user);
        AlertSetting setting = alertSettingRepository.findByPortfolioId(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        return AlertSettingResponse.from(setting);
    }

    /**
     * 알림 설정 수정.
     */
    @Transactional
    public AlertSettingResponse updateAlertSetting(Long portfolioId, User user, AlertSettingUpdateRequest request) {
        Portfolio portfolio = validatePortfolioOwnership(portfolioId, user);
        AlertSetting setting = alertSettingRepository.findByPortfolioId(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));

        setting.update(request.getDiscordWebhookUrl(), request.getDeviationThreshold());
        setting.activate(); // 수정 시 다시 활성화

        return AlertSettingResponse.from(setting);
    }

    /**
     * 알림 설정 초기화 (비활성화 및 URL 제거).
     */
    @Transactional
    public void resetAlertSetting(Long portfolioId, User user) {
        Portfolio portfolio = validatePortfolioOwnership(portfolioId, user);
        AlertSetting setting = alertSettingRepository.findByPortfolioId(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));

        setting.update(null, setting.getDeviationThreshold());
        setting.deactivate();
    }

    /**
     * 알림 이력 조회 (커서 기반 페이징).
     */
    public CursorPageResponse<AlertHistoryResponse> getAlertHistories(Long portfolioId, User user, Long cursorId, int size) {
        validatePortfolioOwnership(portfolioId, user);

        Slice<AlertHistory> slice = alertHistoryRepository.findByPortfolioId(portfolioId, cursorId, PageRequest.of(0, size));

        List<AlertHistoryResponse> content = slice.getContent().stream()
                .map(AlertHistoryResponse::from)
                .collect(Collectors.toList());

        Long nextCursor = content.isEmpty() ? null : content.get(content.size() - 1).getId();

        return CursorPageResponse.of(content, nextCursor, slice.hasNext());
    }

    /**
     * 포트폴리오 소유권 검증 (IDOR 방어).
     */
    private Portfolio validatePortfolioOwnership(Long portfolioId, User user) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (!portfolio.getUser().getId().equals(user.getId())) {
            // 보안을 위해 타인의 포트폴리오 접근 시 404와 동일하게 처리
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }
        return portfolio;
    }
}
