package com.coinvest.portfolio.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.repository.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioCreateRequest;
import com.coinvest.portfolio.dto.PortfolioResponse;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @InjectMocks
    private PortfolioService portfolioService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AlertSettingRepository alertSettingRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
    }

    @Test
    @DisplayName("정상적인 비중 합(100%)으로 포트폴리오를 생성할 수 있다.")
    void createPortfolioSuccess() {
        // given
        PortfolioCreateRequest.AssetRequest assetReq1 = new PortfolioCreateRequest.AssetRequest("CRYPTO:BTC", new BigDecimal("0.7"));
        PortfolioCreateRequest.AssetRequest assetReq2 = new PortfolioCreateRequest.AssetRequest("CRYPTO:ETH", new BigDecimal("0.3"));
        PortfolioCreateRequest request = new PortfolioCreateRequest("My Portfolio", new BigDecimal("1000000"), Currency.KRW, Arrays.asList(assetReq1, assetReq2));

        Asset btc = Asset.builder().universalCode("CRYPTO:BTC").quoteCurrency(Currency.KRW).build();
        Asset eth = Asset.builder().universalCode("CRYPTO:ETH").quoteCurrency(Currency.KRW).build();

        given(portfolioRepository.countByUser(user)).willReturn(0L);
        given(assetRepository.findByUniversalCode("CRYPTO:BTC")).willReturn(Optional.of(btc));
        given(assetRepository.findByUniversalCode("CRYPTO:ETH")).willReturn(Optional.of(eth));
        given(portfolioRepository.save(any(Portfolio.class))).willAnswer(invocation -> {
            Portfolio p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        // when
        PortfolioResponse response = portfolioService.createPortfolio(user, request);

        // then
        assertThat(response.getName()).isEqualTo("My Portfolio");
        assertThat(response.getAssets()).hasSize(2);
        verify(eventPublisher, times(1)).publishEvent(any(com.coinvest.portfolio.dto.PortfolioUpdatedEvent.class));
    }

    @Test
    @DisplayName("비중 합이 100%가 아니면 예외가 발생한다.")
    void createPortfolioInvalidWeight() {
        // given
        PortfolioCreateRequest.AssetRequest asset1 = new PortfolioCreateRequest.AssetRequest("CRYPTO:BTC", new BigDecimal("0.7"));
        PortfolioCreateRequest.AssetRequest asset2 = new PortfolioCreateRequest.AssetRequest("CRYPTO:ETH", new BigDecimal("0.2")); // 합 0.9
        PortfolioCreateRequest request = new PortfolioCreateRequest("Invalid Portfolio", new BigDecimal("1000000"), Currency.KRW, Arrays.asList(asset1, asset2));

        given(portfolioRepository.countByUser(user)).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> portfolioService.createPortfolio(user, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PORTFOLIO_INVALID_WEIGHT.getMessage());
    }

    @Test
    @DisplayName("타인의 포트폴리오를 조회하려고 하면 PORTFOLIO_NOT_FOUND 예외가 발생한다.")
    void getPortfolioForbidden() {
        // given
        User otherUser = User.builder().id(2L).build();
        Portfolio portfolio = Portfolio.builder().id(100L).user(otherUser).build();
        given(portfolioRepository.findById(100L)).willReturn(Optional.of(portfolio));

        // when & then
        assertThatThrownBy(() -> portfolioService.getPortfolio(100L, user))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PORTFOLIO_NOT_FOUND.getMessage());
    }
}
