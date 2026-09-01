package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDateTime;

// executedAt은 실제 실행(확정)시각(api_conventions.md §6-4) - SUCCESS/FAILED만 채워짐
// canceledAt은 취소 시각 - CANCELED만 채워짐. 둘은 상태에 따라 배타적으로 하나만 채워진다(PR #223 리뷰, vsopsw)
public record ScheduledTransferExecutionResultItem(
        Long scheduledTransferId,
        ScheduledTransferStatus status,
        LocalDateTime executedAt,
        LocalDateTime canceledAt,
        String withdrawalAccountNumber,
        String payeeAccountNumber,
        String payeeName,
        Long amount,
        String transactionNumber,
        String failureReason) {}
