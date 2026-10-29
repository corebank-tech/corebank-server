package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.Builder;

@Builder
public record TransferResult(
        ProcessResultStatus status,
        String transactionNumber,
        LocalDateTime transferredAt,
        String errorCode,
        String errorMessage,
        Long withdrawalBalanceAfter) {

    /** 알림에 붙일 고객용 실패 사유. 실패이고 고객이 조치할 수 있을 때만 있다(#707). */
    public Optional<String> customerFailureReason() {
        boolean isFailed = status == ProcessResultStatus.ERROR;
        if (!isFailed) {
            return Optional.empty();
        }
        return TransferErrorCode.customerActionableMessage(errorCode);
    }
}
