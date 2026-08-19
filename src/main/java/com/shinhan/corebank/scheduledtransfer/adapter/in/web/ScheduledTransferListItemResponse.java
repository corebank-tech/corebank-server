package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;

public record ScheduledTransferListItemResponse(Long scheduledTransferId, LocalDate scheduledDate,
                                                String withdrawalAccountNumber, String payeeBankName,
                                                String accountNumber, String payeeName, Long amount,
                                                ScheduledTransferStatus status, boolean cancelable) {

    private static final String BANK_CODE_SHINHAN = "088";
    private static final String BANK_NAME_SHINHAN = "신한은행";

    public static ScheduledTransferListItemResponse from(ScheduledTransferListItem item) {
        return new ScheduledTransferListItemResponse(
                item.scheduledTransferId(),
                item.scheduledDate(),
                MaskingUtil.maskAccountNumber(item.withdrawalAccountNumber()),
                resolveBankName(item.payeeBankCode()),
                MaskingUtil.maskAccountNumber(item.payeeAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.status(),
                item.cancelable()
        );
    }

    // 1차는 당행 전용 — 은행 테이블 없이 로컬 상수로 변환 (scheduled_transfer.payee_bank_code 스키마 주석 참고)
    private static String resolveBankName(String bankCode) {
        return BANK_CODE_SHINHAN.equals(bankCode) ? BANK_NAME_SHINHAN : bankCode;
    }
}
