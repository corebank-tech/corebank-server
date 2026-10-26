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

    /** transfer 행이 생기기 전의 사전검증에서 실패했다. 이체 엔진이 확정 이벤트를 발행하지 못한 경우다. */
    public boolean isPreValidationFailure() {
        return status == ProcessResultStatus.ERROR && transactionNumber == null;
    }

    /** 알림에 붙일 고객용 실패 사유. 실패이고 고객이 조치할 수 있을 때만 있다(#707). */
    public Optional<String> customerFailureReason() {
        boolean isFailed = status == ProcessResultStatus.ERROR;
        if (!isFailed) {
            return Optional.empty();
        }
        return TransferErrorCode.customerActionableMessage(errorCode);
    }
}
