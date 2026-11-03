package com.shinhan.corebank.autotransfer.api;

import java.time.LocalDateTime;

/** 계좌 종료 트랜잭션 내부에서 연결 자동이체를 정리한다. */
public interface AutoTransferAccountClosureUseCase {

    void terminateLinkedTransfers(Long accountId, String accountNumber, LocalDateTime now);
}
