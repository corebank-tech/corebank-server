package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 필드명은 api_conventions.md §6-2/§6-4 확정 명칭(executedAt/accountNumber/failureReason)을 따른다 - FE가 직접 의존하는 외부 응답 계약.
// executedAt은 §6-1(REQ-CMN-016)대로 오프셋 포함 ISO-8601로 내려간다 - 도메인 Transfer.transferredAt은
// LocalDateTime(KST 암묵)이라 응답 경계에서 KST 오프셋을 붙인다
public record TransferHistoryItemResponse(
        @Schema(description = "통합 이력 식별자. 보류에서 시작된 이체는 승인 전후 동일한 HOLD:{transferHoldId} 값") String historyId,
        @Schema(description = "보류 식별자. 일반 이체는 null") Long transferHoldId,
        @Schema(description = "거래번호. 아직 실행되지 않은 HELD/REJECTED는 null", nullable = true) String transactionNumber,
        @Schema(
                        description = "고객용 처리상태",
                        allowableValues = {"SUCCESS", "ERROR", "TIMEOUT", "HELD", "REJECTED"})
                TransferHistoryStatus status,
        OffsetDateTime executedAt,
        String accountNumber,
        String payeeName,
        long amount,
        TransferType transferType,
        TransferChannel channel,
        String errorCode,
        String failureReason) {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public static TransferHistoryItemResponse from(TransferHistoryItem item) {
        return new TransferHistoryItemResponse(
                item.historyId(),
                item.transferHoldId(),
                item.transactionNumber(),
                item.status(),
                item.transferredAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                MaskingUtil.maskAccountNumber(item.depositAccountNumber()),
                item.payeeName() == null ? null : MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.transferType(),
                item.channel(),
                item.errorCode(),
                item.errorMessage());
    }
}
