package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AutoTransferListItemResponse(
        @Schema(description = "자동이체 ID")
        Long autoTransferId,
        @Schema(description = "입금계좌번호")
        String depositAccountNumber,
        @Schema(description = "출금계좌 별칭 (미설정 시 null)")
        String fromAlias,
        @Schema(description = "예금주명")
        String payeeName,
        @Schema(description = "회당 이체금액")
        Long amount,
        @Schema(description = "이체 시작일")
        LocalDate startDate,
        @Schema(description = "이체 종료일")
        LocalDate endDate,
        @Schema(description = "이체지정일")
        Integer transferDay,
        @Schema(description = "이체주기(개월)")
        Integer cycleMonths,
        @Schema(description = "내 통장 표시내용")
        String myPassbookMemo,
        @Schema(description = "자동이체 상태")
        AutoTransferStatus status,
        @Schema(description = "등록일시")
        LocalDateTime registeredAt) {
    public static AutoTransferListItemResponse from(AutoTransferListItem item) {
        return new AutoTransferListItemResponse(item.autoTransferId(), item.depositAccountNumber(), item.fromAlias(),
                item.payeeName(), item.amount(), item.startDate(), item.endDate(), item.transferDay(), item.cycleMonths(),
                item.myPassbookMemo(), item.status(), item.registeredAt());
    }
}
