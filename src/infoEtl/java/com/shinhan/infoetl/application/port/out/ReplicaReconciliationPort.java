package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;

public interface ReplicaReconciliationPort {

    // 원장 입금 합계 - 출금 합계 (거래일 <= baseDate)
    long readLedgerTotal(LocalDate baseDate);

    // GL 20100 대변 합계 - 차변 합계 (fromExclusive < 거래일 <= toInclusive)
    long readGlDepositMovement(LocalDate fromExclusive, LocalDate toInclusive);
}
