package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record TransferResponse(
        @Schema(
                        description =
                                "이체 처리 결과. SUCCESS · ERROR · TIMEOUT. TIMEOUT은 처리 불명(대외 응답 없음)으로 실패가 아니다 — 재시도하지 말고 결과 확인 중으로 안내한다")
                ProcessResultStatus status,
        @Schema(
                        description =
                                "거래번호. 채번(순번 발급) 이후 시도부터 채워짐 — status=SUCCESS·TIMEOUT이거나 채번 후 실패면 값이 있고, 채번 전 실패(예: 등록되지 않은 출금계좌)면 비어있음. TIMEOUT 건은 이 값으로 결과를 다시 조회한다",
                        example = "202608190000001")
                String transactionNumber,
        @Schema(description = "이체 처리 시각. status=SUCCESS일 때만 채워짐 (TIMEOUT이면 null)") LocalDateTime transferredAt,
        @Schema(description = "실패 코드. status=ERROR일 때만 채워짐 (예: TRF0303). TIMEOUT이면 null", nullable = true)
                String errorCode,
        @Schema(description = "실패 메시지. status=ERROR일 때만 채워짐. TIMEOUT이면 null", nullable = true) String errorMessage,
        @Schema(description = "이체 후 출금계좌 잔액. status=SUCCESS일 때만 채워짐 (TIMEOUT이면 null)") Long withdrawalBalanceAfter) {
    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.status(),
                result.transactionNumber(),
                result.transferredAt(),
                result.errorCode(),
                result.errorMessage(),
                result.withdrawalBalanceAfter());
    }
}
