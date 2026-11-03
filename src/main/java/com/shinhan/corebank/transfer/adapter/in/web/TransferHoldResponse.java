package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record TransferHoldResponse(
        @Schema(description = "보류 ID. 취소할 때 쓴다", example = "1") long transferHoldId,
        @Schema(description = "보류 상태. 즉시이체 응답에서는 항상 HELD", example = "HELD") String status,
        @Schema(description = "실행 예정 시각. 승인으로 푸는 보류면 null", nullable = true) LocalDateTime releaseAt,
        @Schema(description = "취소할 수 있는 마지막 시각(실행 30분 전). 승인으로 푸는 보류면 null", nullable = true)
                LocalDateTime cancellableUntil) {

    static TransferHoldResponse heldFrom(TransferResult result) {
        return new TransferHoldResponse(result.transferHoldId(), "HELD", result.releaseAt(), result.cancellableUntil());
    }
}
