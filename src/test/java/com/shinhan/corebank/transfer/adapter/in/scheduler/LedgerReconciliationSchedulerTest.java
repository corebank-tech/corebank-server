package com.shinhan.corebank.transfer.adapter.in.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationSchedulerTest {

    // UTC로는 3/15지만 서울로는 3/16 01:00인 시각 — 시계 zone이 아니라 Asia/Seoul로 날짜를 잡는지 가른다
    private static final Clock UTC_CLOCK = Clock.fixed(Instant.parse("2026-03-15T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    LedgerReconciliationBatchUseCase ledgerReconciliationBatchUseCase;

    @Test
    @DisplayName("일일 대사는 Asia/Seoul 기준 전일 날짜로 유스케이스를 호출한다")
    void runDailyReconciliation_delegatesWithYesterdayInSeoul() {
        new LedgerReconciliationScheduler(ledgerReconciliationBatchUseCase, UTC_CLOCK).runDailyReconciliation();

        verify(ledgerReconciliationBatchUseCase).run(LocalDate.of(2026, 3, 15));
        verifyNoMoreInteractions(ledgerReconciliationBatchUseCase);
    }

    @Test
    @DisplayName("주간 전수 대사는 전수 대사 유스케이스만 호출한다")
    void runWeeklyFullReconciliation_delegatesToRunFull() {
        new LedgerReconciliationScheduler(ledgerReconciliationBatchUseCase, UTC_CLOCK).runWeeklyFullReconciliation();

        verify(ledgerReconciliationBatchUseCase).runFull();
        verifyNoMoreInteractions(ledgerReconciliationBatchUseCase);
    }
}
