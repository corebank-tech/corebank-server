package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDateTime;
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
}
