package com.coinvest.portfolio.service;

import com.coinvest.auth.domain.User;
import com.coinvest.portfolio.domain.Portfolio;
import com.coinvest.portfolio.domain.PortfolioRepository;
import com.coinvest.portfolio.dto.PortfolioValuation;
import com.coinvest.portfolio.dto.RebalancingProposal;
import com.coinvest.portfolio.repository.AlertSettingRepository;
import com.coinvest.trading.domain.VirtualAccount;
import com.coinvest.trading.repository.VirtualAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("리밸런싱 시뮬레이션: 가용 잔고 부족 시 매수 제안 수량이 보정되어야 함")
    void simulateRebalancingWithBalanceCorrection() {
        // given
        Long portfolioId = 1L;
        User user = User.builder().id(100L).build();
        Portfolio portfolio = Portfolio.builder().id(portfolioId).user(user).build();
        
        // 가용 잔고 10,000 KRW
        VirtualAccount account = VirtualAccount.builder()
                .balanceKrw(new BigDecimal("10000"))
                .lockedKrw(BigDecimal.ZERO)
                .build();

        // 현재 BTC 60%, ETH 40% 상태 / 총 평가액 100,000 KRW
        // BTC 목표 80%, ETH 목표 20%로 리밸런싱 시도
        PortfolioValuation valuation = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationKrw(new BigDecimal("100000"))
                .assetValuations(Arrays.asList(
                        PortfolioValuation.AssetValuation.builder()
                                .marketCode("KRW-BTC")
                                .currentPrice(new BigDecimal("100000")) // BTC 가격 10만
                                .currentEvaluationKrw(new BigDecimal("60000")) // 현재 60%
                                .currentWeight(new BigDecimal("0.6000"))
                                .targetWeight(new BigDecimal("0.8000")) // 목표 80% (필요 매수: 20,000 KRW)
                                .build()
                ))
                .build();

        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(virtualAccountRepository.findByUserId(user.getId())).willReturn(Optional.of(account));
        given(valuationService.evaluate(portfolioId)).willReturn(valuation);

        // when
        List<RebalancingProposal> proposals = rebalancingService.simulateRebalancing(portfolioId);

        // then
        RebalancingProposal btcProposal = proposals.get(0);
        assertThat(btcProposal.getAction()).isEqualTo(RebalancingProposal.Action.BUY);
        
        // 필요 금액은 20,000원(0.2개)이지만, 가용 잔고가 10,000원이므로 수수료 제외한 최대치로 보정되어야 함
        // 10000 / (100000 * 1.0005) = 0.09995002...
        assertThat(btcProposal.getProposedQuantity()).isLessThan(new BigDecimal("0.2"));
        assertThat(btcProposal.getProposedQuantity().multiply(btcProposal.getCurrentPrice()))
                .isLessThanOrEqualTo(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("편차 0.01% 이내일 경우 HOLD로 처리되어야 함 (Tolerance 검증)")
    void simulateRebalancingWithTolerance() {
        // given
        Long portfolioId = 1L;
        User user = User.builder().id(100L).build();
        Portfolio portfolio = Portfolio.builder().id(portfolioId).user(user).build();
        VirtualAccount account = VirtualAccount.builder().balanceKrw(new BigDecimal("1000000")).build();

        // 0.0001(0.01%)의 미세한 편차 발생
        PortfolioValuation valuation = PortfolioValuation.builder()
                .portfolioId(portfolioId)
                .totalEvaluationKrw(new BigDecimal("100000"))
                .assetValuations(Arrays.asList(
                        PortfolioValuation.AssetValuation.builder()
                                .marketCode("KRW-BTC")
                                .currentPrice(new BigDecimal("100000"))
                                .currentEvaluationKrw(new BigDecimal("50010")) 
                                .currentWeight(new BigDecimal("0.5001")) // 현재 50.01%
                                .targetWeight(new BigDecimal("0.5000"))  // 목표 50.00%
                                .build()
                ))
                .build();

        given(portfolioRepository.findById(portfolioId)).willReturn(Optional.of(portfolio));
        given(virtualAccountRepository.findByUserId(user.getId())).willReturn(Optional.of(account));
        given(valuationService.evaluate(portfolioId)).willReturn(valuation);

        // when
        List<RebalancingProposal> proposals = rebalancingService.simulateRebalancing(portfolioId);

        // then
        assertThat(proposals.get(0).getAction()).isEqualTo(RebalancingProposal.Action.HOLD);
        assertThat(proposals.get(0).getProposedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
