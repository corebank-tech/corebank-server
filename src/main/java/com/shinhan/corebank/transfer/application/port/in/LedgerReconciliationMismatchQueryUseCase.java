package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// 관리자 대사 불일치 목록(PH-38, FE ADM-04) 조회.
public interface LedgerReconciliationMismatchQueryUseCase {

    /** businessDate가 null이면 가장 최근에 불일치가 기록된 영업일을 보여준다. */
    LedgerReconciliationMismatchList find(LocalDate businessDate, Pageable pageable);

    /**
     * @param businessDate 보여주는 영업일. 기록이 하나도 없으면 null
     */
    record LedgerReconciliationMismatchList(
            LocalDate businessDate, Page<LedgerReconciliationMismatchView> mismatches) {}

    record LedgerReconciliationMismatchView(
            Long accountId, long ledgerBalance, long accountBalance, LocalDateTime detectedAt) {

        // 원장 - 잔액. 양수면 잔액이 원장보다 적다
        public long difference() {
            return ledgerBalance - accountBalance;
        }
    }
}
