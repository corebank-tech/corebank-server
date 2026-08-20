package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDateTime;

// executedAt은 실제 실행(확정)시각(api_conventions.md §6-4) - SUCCESS/FAILED만 채워짐
// canceledAt은 취소 시각 - CANCELED만 채워짐. FE는 상태에 따라 둘 중 채워진 쪽을 표시일로 사용(PR #223 리뷰, vsopsw)
public record ScheduledTransferExecutionResultItemResponse(Long scheduledTransferId,
                                                            ScheduledTransferStatus status,
                                                            LocalDateTime executedAt,
                                                            LocalDateTime canceledAt,
                                                            String withdrawalAccountNumber,
                                                            String accountNumber,
                                                            String payeeName,
                                                            Long amount,
                                                            String transactionNumber,
                                                            String failureReason) {
    public static ScheduledTransferExecutionResultItemResponse from(ScheduledTransferExecutionResultItem item) {
        return new ScheduledTransferExecutionResultItemResponse(
                item.scheduledTransferId(),
                item.status(),
                item.executedAt(),
                item.canceledAt(),
                MaskingUtil.maskAccountNumber(item.withdrawalAccountNumber()),
                MaskingUtil.maskAccountNumber(item.payeeAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.transactionNumber(),
                item.failureReason()
        );
    }
}
