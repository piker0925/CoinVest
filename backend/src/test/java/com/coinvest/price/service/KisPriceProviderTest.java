package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
import com.coinvest.fx.domain.Currency;
import com.coinvest.global.util.Sleeper;
import com.coinvest.portfolio.repository.AlertHistoryRepository;
import com.coinvest.price.dto.TickerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KisPriceProviderTest {

    @Mock
    private HttpClient httpClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KisApiManager kisApiManager;

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Mock
    private Sleeper sleeper;

    @InjectMocks
    private KisPriceProvider kisPriceProvider;

    private Asset testAsset;

    @BeforeEach
    void setUp() throws Exception {
        testAsset = Asset.builder()
                .universalCode("KR_STOCK:005930")
                .externalCode("005930")
                .assetClass(AssetClass.KR_STOCK)
                .quoteCurrency(Currency.KRW)
                .build();

        doNothing().when(sleeper).sleep(anyLong());
        when(kisApiManager.getAccessToken()).thenReturn("valid_token");
        when(kisApiManager.getBaseUrl()).thenReturn("http://localhost");
        when(kisApiManager.getAppKey()).thenReturn("key");
        when(kisApiManager.getAppSecret()).thenReturn("secret");
    }

    @Test
    @DisplayName("KIS 가격 조회 성공 검증")
    void fetchPrices_Success() throws Exception {
        // given
        String jsonResponse = "{\"output\": {\"stck_prpr\": \"75000\"}}";
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(jsonResponse);

        when(httpClient.<String>send(any(), any())).thenReturn(response);

        // when
        List<TickerEvent> results = kisPriceProvider.fetchPrices(List.of(testAsset));

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTradePrice()).isEqualByComparingTo("75000");
        verify(httpClient, atLeastOnce()).send(any(), any());
    }

    @Test
    @DisplayName("KIS API 실패 시 재시도 로직 검증")
    void fetchPrices_Retry_Logic() throws Exception {
        // given
        HttpResponse<String> failResponse = mock(HttpResponse.class);
        when(failResponse.statusCode()).thenReturn(500);
        when(failResponse.body()).thenReturn("Error");

        when(httpClient.<String>send(any(), any())).thenReturn(failResponse);

        // when
        kisPriceProvider.fetchPrices(List.of(testAsset));

        // then
        // MAX_RETRIES=3 이므로 3번 호출 시도 확인
        verify(httpClient, times(3)).send(any(), any());
        // 백오프 sleep 호출 확인 (최소 초기 jitter 1회 + 백오프 2회 = 3회 이상)
        verify(sleeper, atLeast(3)).sleep(anyLong());
    }
}
