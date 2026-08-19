package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;

public record ScheduledTransferListItemResponse(Long scheduledTransferId, LocalDate scheduledDate,
                                                 String withdrawalAccountNumber, String payeeBankName,
                                                 String payeeAccountNumber, String payeeName, Long amount,
                                                 ScheduledTransferStatus status, boolean cancelable) {

    private static final String BANK_CODE_SHINHAN = "088";
    private static final String BANK_NAME_SHINHAN = "신한은행";

    public static ScheduledTransferListItemResponse from(ScheduledTransfer scheduledTransfer, String rawWithdrawalAccountNumber) {
        return new ScheduledTransferListItemResponse(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getScheduledDate(),
                MaskingUtil.maskAccountNumber(rawWithdrawalAccountNumber),
                resolveBankName(scheduledTransfer.getPayeeBankCode()),
                MaskingUtil.maskAccountNumber(scheduledTransfer.getPayeeAccountNumber()),
                MaskingUtil.maskName(scheduledTransfer.getPayeeName()),
                scheduledTransfer.getAmount(),
                scheduledTransfer.getStatus(),
                scheduledTransfer.getStatus() == ScheduledTransferStatus.WAITING
        );
    }

    // 1차는 당행 전용 — 은행 테이블 없이 로컬 상수로 변환 (scheduled_transfer.payee_bank_code 스키마 주석 참고)
    private static String resolveBankName(String bankCode) {
        return BANK_CODE_SHINHAN.equals(bankCode) ? BANK_NAME_SHINHAN : bankCode;
    }
}
