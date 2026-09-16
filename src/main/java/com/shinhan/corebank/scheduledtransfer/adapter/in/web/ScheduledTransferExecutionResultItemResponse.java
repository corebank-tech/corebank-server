package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// executedAt은 실제 실행(확정)시각(api_conventions.md §6-4) - SUCCESS/FAILED만 채워짐
// canceledAt은 취소 시각 - CANCELED만 채워짐. FE는 상태에 따라 둘 중 채워진 쪽을 표시일로 사용(PR #223 리뷰, vsopsw)
public record ScheduledTransferExecutionResultItemResponse(
        @Schema(description = "예약이체 ID") Long scheduledTransferId,
        @Schema(description = "처리결과 상태") ScheduledTransferStatus status,
        @Schema(description = "실행(확정) 시각. SUCCESS/FAILED만 채워짐", nullable = true) LocalDateTime executedAt,
        @Schema(description = "취소 시각. CANCELED만 채워짐", nullable = true) LocalDateTime canceledAt,
        @Schema(description = "출금계좌번호 (마스킹, 예: 110******877)") String withdrawalAccountNumber,
        @Schema(description = "입금계좌번호 (마스킹, 예: 110******877)") String accountNumber,
        @Schema(description = "예금주명 (마스킹, 예: 홍*동)") String payeeName,
        @Schema(description = "이체금액") Long amount,
        @Schema(description = "거래번호. SUCCESS/FAILED만 채워짐", nullable = true) String transactionNumber,
        @Schema(description = "실패 사유. status=SUCCESS일 때는 비어있음", nullable = true) String failureReason) {
    public static ScheduledTransferExecutionResultItemResponse from(ScheduledTransferExecutionResultItem item) {
        return new ScheduledTransferExecutionResultItemResponse(
                item.scheduledTransferId(),
                item.status(),
                item.executedAt(),
                item.canceledAt(),
                MaskingUtil.maskAccountNumber(item.withdrawalAccountNumber()),
                MaskingUtil.maskAccountNumber(item.payeeAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.transactionNumber(),
                item.failureReason());
    }
}
