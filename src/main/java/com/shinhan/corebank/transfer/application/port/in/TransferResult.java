package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.Builder;

/**
 * 이체 실행 결과. 훅 C가 보류했으면 이체가 아직 없어 status가 null이고 transferHoldId가 있다(transfer_seam.md §9-3).
 *
 * @param transferHoldId 보류했으면 보류 ID. 그 밖에는 null
 * @param releaseAt      시각으로 푸는 보류(지연이체)의 실행 예정 시각. 그 밖에는 null
 */
@Builder
public record TransferResult(
        ProcessResultStatus status,
        String transactionNumber,
        LocalDateTime transferredAt,
        String errorCode,
        String errorMessage,
        Long withdrawalBalanceAfter,
        Long transferHoldId,
        LocalDateTime releaseAt) {

    public boolean held() {
        return transferHoldId != null;
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
