package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferCancelResult;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AutoTransferCancelItemResponse(
        @Schema(description = "자동이체 ID") Long autoTransferId,
        @Schema(description = "건별 처리결과. SUCCESS(해지됨)/ERROR(해지 불가)") ProcessResultStatus status,
        @Schema(description = "해지 시각. 실패 건은 null", nullable = true) LocalDateTime terminatedAt,
        @Schema(description = "실패 오류코드(`AUT0201`·`AUT0302`·`AUT0303`). 성공 건은 null", nullable = true) String failureCode,
        @Schema(description = "실패 사유. 성공 건은 null", nullable = true) String failureReason) {

    public static AutoTransferCancelItemResponse from(AutoTransferCancelResult result) {
        return new AutoTransferCancelItemResponse(
                result.autoTransferId(),
                result.status(),
                result.terminatedAt(),
                result.failureCode(),
                result.failureReason());
    }
}
