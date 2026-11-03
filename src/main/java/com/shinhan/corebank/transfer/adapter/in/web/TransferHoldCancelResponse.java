package com.shinhan.corebank.transfer.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransferHoldCancelResponse(
        @Schema(description = "취소한 보류 ID", example = "1") long transferHoldId,
        @Schema(description = "취소 뒤 보류 상태", example = "CANCELLED") String status) {}
