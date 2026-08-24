package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelResult;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ScheduledTransferCancelItemResponse(
        @Schema(description = "예약이체 ID")
        Long scheduledTransferId,
        @Schema(description = "건별 처리결과. SUCCESS(취소됨)/ERROR(취소 불가)")
        ProcessResultStatus status,
        @Schema(description = "취소 시각. 실패 건은 null")
        LocalDateTime canceledAt,
        @Schema(description = "실패 오류코드(`SCD0201`·`SCD0302`·`SCD0303`). 성공 건은 null")
        String failureCode,
        @Schema(description = "실패 사유. 성공 건은 null")
        String failureReason) {

    public static ScheduledTransferCancelItemResponse from(ScheduledTransferCancelResult result) {
        return new ScheduledTransferCancelItemResponse(
                result.scheduledTransferId(),
                result.status(),
                result.canceledAt(),
                result.failureCode(),
                result.failureReason());
    }
}
