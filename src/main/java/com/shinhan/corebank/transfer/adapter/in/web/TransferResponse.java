package com.shinhan.corebank.transfer.adapter.in.web;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransferResponse(
        @Schema(description = "이체 처리 결과. SUCCESS/ERROR/PROCESSING(응답 유실 등으로 결과 미확정)")
        ProcessResultStatus status,
        @Schema(description = "거래번호. 채번(순번 발급) 이후 시도부터 채워짐 — status=SUCCESS이거나 채번 후 실패면 값이 있고, 채번 전 실패(예: 등록되지 않은 출금계좌)면 비어있음", example = "202608190000001")
        String transactionNumber,
        @Schema(description = "이체 처리 시각. status=SUCCESS일 때만 채워짐")
        LocalDateTime transferredAt,
        @Schema(description = "실패 코드. status=ERROR일 때만 채워짐 (예: TRF0303)", nullable = true)
        String errorCode,
        @Schema(description = "실패 메시지. status=ERROR일 때만 채워짐", nullable = true)
        String errorMessage,
        @Schema(description = "이체 후 출금계좌 잔액. status=SUCCESS일 때만 채워짐")
        Long withdrawalBalanceAfter
) {
    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.status(),
                result.transactionNumber(),
                result.transferredAt(),
                result.errorCode(),
                result.errorMessage(),
                result.withdrawalBalanceAfter()
        );
    }
}
