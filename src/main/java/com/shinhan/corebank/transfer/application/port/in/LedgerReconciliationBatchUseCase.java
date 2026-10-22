package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDate;

// 대사(#378)를 배치 잡으로 실행한다. LedgerReconciliationUseCase(탐지 로직)를 batch 모듈의
// 실행 락으로 감싸, 여러 인스턴스에서 중복 실행되지 않도록 한다.
public interface LedgerReconciliationBatchUseCase {

    void run(LocalDate date);

    // 전 계좌 전수 대사(#468)를 같은 락으로 실행한다. 증분 대사와 동시에 돌지 않는다.
    void runFull();

    // COB 대사 스텝(#604)이 부른다. 전수 대사 후 불일치를 영업일별로 저장(#613)하고 건수로 soft/hard를 판정한다.
    void runInCob(LocalDate cobDate);
}
