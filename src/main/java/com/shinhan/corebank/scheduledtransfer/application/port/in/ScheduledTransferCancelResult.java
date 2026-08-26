package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;

import java.time.LocalDateTime;

// 다건 취소의 건별 처리결과. 요청 자체는 성공했고 건별 성공/실패는 데이터이므로 예외가 아니라 값으로 돌려준다.
public record ScheduledTransferCancelResult(Long scheduledTransferId, ProcessResultStatus status,
                                            LocalDateTime canceledAt, String failureCode, String failureReason) {

    public static ScheduledTransferCancelResult success(ScheduledTransfer scheduledTransfer) {
        return new ScheduledTransferCancelResult(scheduledTransfer.getScheduledTransferId(),
                ProcessResultStatus.SUCCESS, scheduledTransfer.getCanceledAt(), null, null);
    }

    public static ScheduledTransferCancelResult failure(Long scheduledTransferId, ErrorCode errorCode) {
        return new ScheduledTransferCancelResult(scheduledTransferId, ProcessResultStatus.ERROR,
                null, errorCode.getCode(), errorCode.getMessage());
    }
}
