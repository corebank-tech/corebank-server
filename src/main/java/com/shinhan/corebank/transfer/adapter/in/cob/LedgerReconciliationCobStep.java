package com.shinhan.corebank.transfer.adapter.in.cob;

import com.shinhan.corebank.batch.api.CobStep;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// COB 체인 표 2번 대사 스텝(PH-38, #604). 1번 영업일 전환은 러너가 스텝 루프 전에 직접 한다(ADR-0004).
@Component
@RequiredArgsConstructor
public class LedgerReconciliationCobStep implements CobStep {

    private static final String NAME = "ledger-reconciliation";
    private static final int ORDER = 2;

    private final LedgerReconciliationBatchUseCase ledgerReconciliationBatchUseCase;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    // businessDate는 "새 영업일"이 아니라 지금 마감 중인 COB_DATE다(ADR-0004)
    @Override
    public void run(LocalDate businessDate) {
        ledgerReconciliationBatchUseCase.runInCob(businessDate);
    }
}
