package com.coinvest.trading;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.asset.repository.AssetRepository;
import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.fx.domain.Currency;
import com.coinvest.price.service.PriceService;
import com.coinvest.trading.domain.*;
import com.coinvest.trading.dto.OrderCreateRequest;
import com.coinvest.trading.repository.VirtualAccountRepository;
import com.coinvest.trading.service.TradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TradingIntegrationTest extends com.coinvest.AbstractIntegrationTest {

    @Autowired
    private TradingService tradingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private AssetRepository assetRepository;

    @MockBean
    private PriceService priceService;

    private User testUser;
    private final String universalCode = "CRYPTO:BTC";

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("trading_test@example.com")
                .password("password")
                .nickname("Trader")
                .build());

        VirtualAccount account = VirtualAccount.builder()
                .user(testUser)
                .balances(new ArrayList<>())
                .build();
        virtualAccountRepository.save(account);

        Balance krwBalance = Balance.builder()
                .account(account)
                .currency(Currency.KRW)
                .available(new BigDecimal("10000000"))
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        account.getBalances().add(krwBalance);

        Balance usdBalance = Balance.builder()
                .account(account)
                .currency(Currency.USD)
                .available(BigDecimal.ZERO)
                .locked(BigDecimal.ZERO)
                .unsettled(BigDecimal.ZERO)
                .build();
        account.getBalances().add(usdBalance);

        virtualAccountRepository.save(account);

        assetRepository.save(Asset.builder()
                .universalCode(universalCode)
                .name("Bitcoin")
                .assetClass(AssetClass.CRYPTO)
                .exchangeCode("UPBIT")
                .externalCode("KRW-BTC")
                .quoteCurrency(Currency.KRW)
                .feeRate(new BigDecimal("0.0005"))
                .build());
    }

    @Test
    @DisplayName("통합 테스트: 시장가 매수 주문 체결 후 잔고 및 포지션 확인")
    void market_buy_integration_test() {
        // given
        BigDecimal currentPrice = new BigDecimal("100000000");
        given(priceService.getCurrentPrice(anyString())).willReturn(currentPrice);

        OrderCreateRequest request = new OrderCreateRequest(universalCode, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("0.05"));

        // when
        Long orderId = tradingService.createOrder(testUser.getId(), request);

        // then
        assertThat(orderId).isNotNull();
        VirtualAccount account = virtualAccountRepository.findByUserId(testUser.getId()).orElseThrow();
        // 500만 + 수수료 2500원 차감 -> 4,997,500
        assertThat(account.getAvailableForPurchase(Currency.KRW).compareTo(new BigDecimal("4997500"))).isEqualTo(0);
    }
}
