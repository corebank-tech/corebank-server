package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerReconciliationBatchService implements LedgerReconciliationBatchUseCase {

    private static final String JOB_NAME = "LEDGER_RECONCILIATION_BATCH";

    private final LedgerReconciliationUseCase ledgerReconciliationUseCase;
    private final BatchExecutionLockPort batchExecutionLockPort;

    @Override
    public void run(LocalDate date) {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            log.info("원장-잔액 대사 배치 시작 - date={}", date);
            ledgerReconciliationUseCase.reconcile(date);
            log.info("원장-잔액 대사 배치 종료 - date={}", date);
        } catch (Exception e) {
            log.error("원장-잔액 대사 배치 실패 - date={}", date, e);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
