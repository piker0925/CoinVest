package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
import com.coinvest.fx.domain.Currency;
import com.coinvest.fx.service.ExchangeRateService;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.dto.RebalancingProposal;
import com.coinvest.trading.domain.Balance;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RebalancingServiceTest {

    @InjectMocks
    private RebalancingService rebalancingService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private VirtualAccountRepository virtualAccountRepository;

    @Mock
    private PortfolioValuationService valuationService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("리밸런싱 시뮬레이션: 가용 잔고 부족 시 매수 제안 수량이 보정되어야 함")
    void simulateRebalancingWithBalanceCorrection() {
        // given
        Long portfolioId = 1L;
        User user = User.builder().id(100L).build();
        Portfolio portfolio = Portfolio.builder().id(portfolioId).user(user).baseCurrency(Currency.KRW).build();
        
        VirtualAccount account = VirtualAccount.builder().id(1L).user(user).balances(new ArrayList<>()).build();
        account.getBalances().add(Balance.builder()
                .account(account).currency(Currency.KRW).available(new BigDecimal("10000")).build());

        PortfolioValuation valuation = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationBase(new BigDecimal("100000"))
                .baseCurrency(Currency.KRW)
                .assetValuations(Arrays.asList(
                        PortfolioValuation.AssetValuation.builder()
                                .universalCode("CRYPTO:BTC")
                                .currentPrice(new BigDecimal("100000"))
                                .evaluationBase(new BigDecimal("60000"))
                                .fxRate(BigDecimal.ONE)
                                .currentWeight(new BigDecimal("0.6000"))
                                .targetWeight(new BigDecimal("0.8000"))
                                .build()
                ))
                .build();

        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(virtualAccountRepository.findByUserId(user.getId())).willReturn(Optional.of(account));
        given(valuationService.evaluate(portfolioId)).willReturn(valuation);
        given(exchangeRateService.getExchangeRateWithStatus(any(), any()))
                .willReturn(new ExchangeRateService.ExchangeRateResponse(BigDecimal.ONE, false));

        // when
        List<RebalancingProposal> proposals = rebalancingService.simulateRebalancing(portfolioId);

        // then
        RebalancingProposal btcProposal = proposals.get(0);
        assertThat(btcProposal.getAction()).isEqualTo(RebalancingProposal.Action.BUY);
        assertThat(btcProposal.getProposedQuantity()).isLessThan(new BigDecimal("0.2"));
    }

    @Test
    @DisplayName("편차 0.01% 이내일 경우 HOLD로 처리되어야 함 (Tolerance 검증)")
    void simulateRebalancingWithTolerance() {
        // given
        Long portfolioId = 1L;
        User user = User.builder().id(100L).build();
        Portfolio portfolio = Portfolio.builder().id(portfolioId).user(user).baseCurrency(Currency.KRW).build();
        VirtualAccount account = VirtualAccount.builder().id(1L).user(user).balances(new ArrayList<>()).build();
        account.getBalances().add(Balance.builder()
                .account(account).currency(Currency.KRW).available(new BigDecimal("1000000")).build());

        PortfolioValuation valuation = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationBase(new BigDecimal("100000"))
                .baseCurrency(Currency.KRW)
                .assetValuations(Arrays.asList(
                        PortfolioValuation.AssetValuation.builder()
                                .universalCode("CRYPTO:BTC")
                                .currentPrice(new BigDecimal("100000"))
                                .evaluationBase(new BigDecimal("50010"))
                                .fxRate(BigDecimal.ONE)
                                .currentWeight(new BigDecimal("0.5001"))
                                .targetWeight(new BigDecimal("0.5000"))
                                .build()
                ))
                .build();

        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(virtualAccountRepository.findByUserId(user.getId())).willReturn(Optional.of(account));
        given(valuationService.evaluate(portfolioId)).willReturn(valuation);
        given(exchangeRateService.getExchangeRateWithStatus(any(), any()))
                .willReturn(new ExchangeRateService.ExchangeRateResponse(BigDecimal.ONE, false));

        // when
        List<RebalancingProposal> proposals = rebalancingService.simulateRebalancing(portfolioId);

        // then
        assertThat(proposals.get(0).getAction()).isEqualTo(RebalancingProposal.Action.HOLD);
    }
}
