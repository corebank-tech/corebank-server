package com.shinhan.corebank.autotransfer.application.port.out;


import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;

public interface AutoTransferExecutionPersistencePort {
    // 즉시 flush 가 되어야 함 - 유니크 제약이 호출 시점에 바로 걸려야 중복 실행 막음
    // flush란? -> 이 메모리에 쌓아둔 걸 실제 DB에 내보내는 동작
    AutoTransferExecution save(AutoTransferExecution execution, Long autoTransferId);
}
