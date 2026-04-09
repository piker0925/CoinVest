package com.coinvest.trading.service;

import com.coinvest.trading.domain.Settlement;
import com.coinvest.trading.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementJobTest {

    @InjectMocks
    private SettlementJob settlementJob;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementService settlementService;

    @Test
    @DisplayName("정산 배치 처리 중 1건이 실패(예외 발생)하더라도, 다음 건들은 정상적으로 처리되어야 한다 (결함 전파 차단)")
    void should_continue_processing_when_one_settlement_fails() {
        // given
        Settlement s1 = Settlement.builder().build();
        ReflectionTestUtils.setField(s1, "id", 1L);

        Settlement s2 = Settlement.builder().build();
        ReflectionTestUtils.setField(s2, "id", 2L);

        Settlement s3 = Settlement.builder().build();
        ReflectionTestUtils.setField(s3, "id", 3L);

        List<Settlement> pendingSettlements = Arrays.asList(s1, s2, s3);

        given(settlementRepository.findAllByStatusAndSettlementDateBefore(
                eq(Settlement.SettlementStatus.PENDING), any(LocalDate.class)))
                .willReturn(pendingSettlements);

        // 2번 정산 건 처리 시에만 예외 발생 (트랜잭션 REQUIRES_NEW 환경 모사)
        doNothing().when(settlementService).processSingleSettlement(1L);
        doThrow(new RuntimeException("DB Connection Timeout or Data Integrity Issue"))
                .when(settlementService).processSingleSettlement(2L);
        doNothing().when(settlementService).processSingleSettlement(3L);

        // when
        settlementJob.runSettlement();

        // then
        // 2번이 실패했음에도 불구하고 루프가 중단되지 않고 1, 2, 3 모두 호출되었는지 검증
        verify(settlementService, times(1)).processSingleSettlement(1L);
        verify(settlementService, times(1)).processSingleSettlement(2L);
        verify(settlementService, times(1)).processSingleSettlement(3L);
    }
}
