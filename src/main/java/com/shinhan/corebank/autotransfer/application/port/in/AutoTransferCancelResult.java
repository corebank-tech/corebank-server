package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.ErrorCode;
import java.time.LocalDateTime;

// 다건 해지의 건별 처리결과. 요청 자체는 성공했고 건별 성공/실패는 데이터이므로 예외가 아니라 값으로 돌려준다.
public record AutoTransferCancelResult(
        Long autoTransferId,
        ProcessResultStatus status,
        LocalDateTime terminatedAt,
        String failureCode,
        String failureReason) {

    public static AutoTransferCancelResult success(AutoTransfer autoTransfer) {
        return new AutoTransferCancelResult(
                autoTransfer.getAutoTransferId(),
                ProcessResultStatus.SUCCESS,
                autoTransfer.getTerminatedAt(),
                null,
                null);
    }

    public static AutoTransferCancelResult failure(Long autoTransferId, ErrorCode errorCode) {
        return new AutoTransferCancelResult(
                autoTransferId, ProcessResultStatus.ERROR, null, errorCode.getCode(), errorCode.getMessage());
    }
}
