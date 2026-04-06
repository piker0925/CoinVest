package com.coinvest.price.service;

import com.coinvest.asset.domain.Asset;
import com.coinvest.asset.domain.AssetClass;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        testAsset = Asset.builder()
                .universalCode("KR_STOCK:005930")
                .externalCode("005930")
                .assetClass(AssetClass.KR_STOCK)
                .build();
    }

    @Test
    @DisplayName("KIS 가격 조회 성공 및 Jitter 동작 확인")
    void fetchPrices_Success() throws Exception {
        // given
        given(kisApiManager.getAccessToken()).willReturn("valid_token");
        given(kisApiManager.getBaseUrl()).willReturn("http://localhost");

        String jsonResponse = "{\"output\": {\"stck_prpr\": \"75000\"}}";
        HttpResponse<String> response = mock(HttpResponse.class);
        given(response.statusCode()).willReturn(200);
        given(response.body()).willReturn(jsonResponse);

        given(httpClient.<String>send(any(), any())).willReturn(response);

        // when
        List<TickerEvent> results = kisPriceProvider.fetchPrices(List.of(testAsset));

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTradePrice()).isEqualByComparingTo("75000");
        
        // Sleeper가 Jitter(0~200ms) 범위 내에서 호출되었는지 확인
        verify(sleeper).sleep(argThat(val -> val >= 0 && val <= 200));
    }

    @Test
    @DisplayName("KIS API 실패 시 3회 지수 백오프 재시도 확인")
    void fetchPrices_Retry_Backoff() throws Exception {
        // given
        given(kisApiManager.getAccessToken()).willReturn("valid_token");
        given(kisApiManager.getBaseUrl()).willReturn("http://localhost");

        HttpResponse<String> failResponse = mock(HttpResponse.class);
        given(failResponse.statusCode()).willReturn(500);
        given(failResponse.body()).willReturn("Error");

        given(httpClient.<String>send(any(), any())).willReturn(failResponse);

        // when
        kisPriceProvider.fetchPrices(List.of(testAsset));

        // then
        // 기본 1회 + 재시도 2회 = 총 3회 호출 시도 (구현상 MAX_RETRIES=3)
        verify(httpClient, times(3)).send(any(), any());
        
        // 백오프 확인 (1초 -> 2초)
        verify(sleeper).sleep(argThat(val -> val >= 1000 && val <= 1200));
        verify(sleeper).sleep(argThat(val -> val >= 2000 && val <= 2200));
    }
}
