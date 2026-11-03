package com.shinhan.corebank.scheduledtransfer.api;

import java.time.LocalDateTime;

/** 계좌 종료 시 연결된 예약이체를 정리한다. */
public interface ScheduledTransferAccountClosureUseCase {

    void cancelLinkedTransfers(Long accountId, String accountNumber, LocalDateTime now);
}
