package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDate;

// 대사(#378)를 배치 잡으로 실행한다. LedgerReconciliationUseCase(탐지 로직)를 batch 모듈의
// 실행 락으로 감싸, 여러 인스턴스에서 중복 실행되지 않도록 한다.
public interface LedgerReconciliationBatchUseCase {

    void run(LocalDate date);
}
