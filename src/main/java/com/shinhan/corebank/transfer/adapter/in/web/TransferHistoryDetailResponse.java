package com.shinhan.corebank.transfer.adapter.in.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryDetail;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

// 필드명은 api_conventions.md §6-2/§6-4 확정 명칭(executedAt/accountNumber/failureReason/balanceAfter)을 따른다 - FE가 직접 의존하는 외부 응답 계약.
// balanceAfter: withdrawalBalanceAfter는 즉시이체 실행 응답(TransferResponse)만의 예외이고 이체 상세는 balanceAfter로 확정(§6-4)
// executedAt은 §6-1(REQ-CMN-016)대로 오프셋 포함 ISO-8601로 내려간다 - 도메인 Transfer.transferredAt은
// LocalDateTime(KST 암묵)이라 응답 경계에서 KST 오프셋을 붙인다
public record TransferHistoryDetailResponse(
        String transactionNumber,
        ProcessResultStatus status,
        String accountNumber,
        String payeeName,
        long amount,
        long fee,
        TransferType transferType,
        TransferChannel channel,
        String myPassbookMemo,
        String recipientPassbookMemo,
        Long balanceAfter,
        String errorCode,
        String failureReason,
        OffsetDateTime executedAt
) {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public static TransferHistoryDetailResponse from(TransferHistoryDetail detail) {
        return new TransferHistoryDetailResponse(
                detail.transactionNumber(),
                detail.status(),
                MaskingUtil.maskAccountNumber(detail.depositAccountNumber()),
                MaskingUtil.maskName(detail.payeeName()),
                detail.amount(),
                detail.fee(),
                detail.transferType(),
                detail.channel(),
                detail.myPassbookMemo(),
                detail.recipientPassbookMemo(),
                detail.withdrawalBalanceAfter(),
                detail.errorCode(),
                detail.errorMessage(),
                detail.transferredAt().atZone(KOREA_ZONE).toOffsetDateTime()
        );
    }
}
