package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ScheduledTransferListItemResponse(
        @Schema(description = "예약이체 ID")
        Long scheduledTransferId,
        @Schema(description = "예약 실행일")
        LocalDate scheduledDate,
        @Schema(description = "출금계좌번호 (마스킹, 예: 110******877)")
        String withdrawalAccountNumber,
        @Schema(description = "출금계좌 별칭 (미설정 시 null)")
        String fromAlias,
        @Schema(description = "입금은행명. 1차는 당행 전용이라 항상 \"신한은행\"", example = "신한은행")
        String payeeBankName,
        @Schema(description = "입금계좌번호 (마스킹, 예: 110******877)")
        String accountNumber,
        @Schema(description = "예금주명 (마스킹, 예: 홍*동)")
        String payeeName,
        @Schema(description = "이체금액")
        Long amount,
        @Schema(description = "내 통장 표시내용")
        String myPassbookMemo,
        @Schema(description = "예약이체 상태")
        ScheduledTransferStatus status,
        @Schema(description = "취소 가능 여부 (상태가 WAITING이고 실행 예정일이 오늘 이후면 true)")
        boolean cancelable,
        @Schema(description = "등록일시")
        LocalDateTime registeredAt) {

    private static final String BANK_CODE_SHINHAN = "088";
    private static final String BANK_NAME_SHINHAN = "신한은행";

    public static ScheduledTransferListItemResponse from(ScheduledTransferListItem item) {
        return new ScheduledTransferListItemResponse(
                item.scheduledTransferId(),
                item.scheduledDate(),
                MaskingUtil.maskAccountNumber(item.withdrawalAccountNumber()),
                item.fromAlias(),
                resolveBankName(item.payeeBankCode()),
                MaskingUtil.maskAccountNumber(item.payeeAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.myPassbookMemo(),
                item.status(),
                item.cancelable(),
                item.registeredAt()
        );
    }

    // 1차는 당행 전용 — 은행 테이블 없이 로컬 상수로 변환 (scheduled_transfer.payee_bank_code 스키마 주석 참고)
    private static String resolveBankName(String bankCode) {
        return BANK_CODE_SHINHAN.equals(bankCode) ? BANK_NAME_SHINHAN : bankCode;
    }
}
