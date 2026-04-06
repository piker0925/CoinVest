package com.coinvest.portfolio.service;

import com.coinvest.global.common.RedisKeyConstants;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioAsset;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 포트폴리오와 자산(UniversalCode) 간의 매핑 정보를 Redis에 관리하는 서비스.
 * 실시간 평가 시 특정 자산의 가격 변화에 영향을 받는 포트폴리오를 O(1)로 찾기 위함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioMappingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PortfolioRepository portfolioRepository;

    /**
     * 서버 기동 시 모든 포트폴리오의 매핑 정보를 Redis에 초기화.
     */
    @PostConstruct
    public void initMappings() {
        log.info("Initializing portfolio-asset mappings in Redis...");
        List<Portfolio> allPortfolios = portfolioRepository.findAll();
        
        // 기존 매핑 초기화 (안전하게 전체 포트폴리오를 순회하며 갱신함)
        for (Portfolio portfolio : allPortfolios) {
            updateMapping(portfolio);
        }
        log.info("Successfully initialized mappings for {} portfolios.", allPortfolios.size());
    }

    /**
     * 포트폴리오 업데이트 이벤트 수신 시 Redis 매핑 갱신.
     */
    @EventListener
    public void onPortfolioUpdated(PortfolioUpdatedEvent event) {
        log.info("Received portfolio update event for ID: {}. Updating Redis mappings.", event.portfolioId());
        
        if (event.type() == PortfolioUpdatedEvent.UpdateType.DELETE) {
            removeMapping(event.portfolioId(), event.universalCodes());
        } else {
            // CREATE, UPDATE의 경우 DB에서 최신 정보를 조회하여 갱신
            portfolioRepository.findById(event.portfolioId())
                    .ifPresent(this::updateMapping);
        }
    }

    /**
     * 특정 포트폴리오의 자산 정보를 Redis Set에 등록.
     */
    private void updateMapping(Portfolio portfolio) {
        Long portfolioId = portfolio.getId();
        for (PortfolioAsset asset : portfolio.getAssets()) {
            String key = RedisKeyConstants.format(RedisKeyConstants.PORTFOLIO_ASSET_MAPPING_KEY, asset.getUniversalCode());
            redisTemplate.opsForSet().add(key, portfolioId);
        }
    }

    /**
     * 포트폴리오 삭제 시 Redis 매핑에서 제거.
     */
    private void removeMapping(Long portfolioId, List<String> marketCodes) {
        for (String marketCode : marketCodes) {
            String key = RedisKeyConstants.format(RedisKeyConstants.PORTFOLIO_ASSET_MAPPING_KEY, marketCode);
            redisTemplate.opsForSet().remove(key, portfolioId);
        }
    }
}
