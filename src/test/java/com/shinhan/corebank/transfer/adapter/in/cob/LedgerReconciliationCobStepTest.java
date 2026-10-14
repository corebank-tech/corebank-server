package com.shinhan.corebank.transfer.adapter.in.cob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationCobStepTest {

    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 13);

    @Mock
    LedgerReconciliationBatchUseCase ledgerReconciliationBatchUseCase;

    @Test
    @DisplayName("대사 스텝은 COB 체인 순서 2번이다 — 영업일 전환 바로 다음(ADR-0004)")
    void order_isTwo() {
        LedgerReconciliationCobStep step = new LedgerReconciliationCobStep(ledgerReconciliationBatchUseCase);

        assertThat(step.order()).isEqualTo(2);
        assertThat(step.name()).isEqualTo("ledger-reconciliation");
    }

    @Test
    @DisplayName("러너가 넘긴 COB_DATE를 그대로 COB 대사 유스케이스에 넘긴다")
    void run_delegatesCobDateToRunInCob() {
        new LedgerReconciliationCobStep(ledgerReconciliationBatchUseCase).run(COB_DATE);

        verify(ledgerReconciliationBatchUseCase).runInCob(COB_DATE);
        verifyNoMoreInteractions(ledgerReconciliationBatchUseCase);
    }
}
