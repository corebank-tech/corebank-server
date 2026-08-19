package com.shinhan.corebank.scheduledtransfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class ScheduledTransfer {
    private Long scheduledTransferId;
    private Long customerId;
    private Long withdrawalAccountId;
    private String payeeBankCode;
    private String payeeAccountNumber;
    private String payeeName;
    private Long amount;
    private LocalDate scheduledDate;
    private String myPassbookMemo;
    private String recipientPassbookMemo;
    private ScheduledTransferStatus status;
    private String transactionNumber;
    private LocalDateTime registeredAt;
    private LocalDateTime executedAt;
    private LocalDateTime canceledAt;
    private String failureReason;

    // 등록
    public static ScheduledTransfer register(
            Long customerId, Long withdrawalAccountId, String payeeBankCode, String payeeAccountNumber,
            String payeeName, Long amount, LocalDate scheduledDate,
            String myPassbookMemo, String recipientPassbookMemo, LocalDateTime now) {

        if (amount <= 0) {
            throw new BusinessException(ScheduledTransferErrorCode.INVALID_AMOUNT);
        }

        LocalDate today = now.toLocalDate();
        if (!scheduledDate.isAfter(today)) {
            throw new BusinessException(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE);
        }
        if (scheduledDate.isAfter(today.plusDays(365))) {
            throw new BusinessException(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE);
        }

        ScheduledTransfer s = new ScheduledTransfer();
        s.customerId = customerId;
        s.withdrawalAccountId = withdrawalAccountId;
        s.payeeBankCode = payeeBankCode;
        s.payeeAccountNumber = payeeAccountNumber;
        s.payeeName = payeeName;
        s.amount = amount;
        s.scheduledDate = scheduledDate;
        s.myPassbookMemo = myPassbookMemo;
        s.recipientPassbookMemo = recipientPassbookMemo;
        s.status = ScheduledTransferStatus.WAITING;
        s.registeredAt = now;
        return s;
    }

    public static ScheduledTransfer reconstitute(
            Long scheduledTransferId, Long customerId, Long withdrawalAccountId, String payeeBankCode,
            String payeeAccountNumber, String payeeName, Long amount, LocalDate scheduledDate,
            String myPassbookMemo, String recipientPassbookMemo, ScheduledTransferStatus status,
            String transactionNumber, LocalDateTime registeredAt, LocalDateTime executedAt,
            LocalDateTime canceledAt, String failureReason) {
        ScheduledTransfer s = new ScheduledTransfer();
        s.scheduledTransferId = scheduledTransferId;
        s.customerId = customerId;
        s.withdrawalAccountId = withdrawalAccountId;
        s.payeeBankCode = payeeBankCode;
        s.payeeAccountNumber = payeeAccountNumber;
        s.payeeName = payeeName;
        s.amount = amount;
        s.scheduledDate = scheduledDate;
        s.myPassbookMemo = myPassbookMemo;
        s.recipientPassbookMemo = recipientPassbookMemo;
        s.status = status;
        s.transactionNumber = transactionNumber;
        s.registeredAt = registeredAt;
        s.executedAt = executedAt;
        s.canceledAt = canceledAt;
        s.failureReason = failureReason;
        return s;
    }

    // 배치 실행 성공
    public void markSuccess(String transactionNumber, LocalDateTime executedAt) {
        this.status = ScheduledTransferStatus.SUCCESS;
        this.transactionNumber = transactionNumber;
        this.executedAt = executedAt;
    }

    // 배치 실행 실패 - 실패해도 transactionNumber가 채워지는 경우가 있어 같이 받는다
    public void markFailed(String failureReason, String transactionNumber, LocalDateTime executedAt) {
        this.status = ScheduledTransferStatus.FAILED;
        this.failureReason = failureReason;
        this.transactionNumber = transactionNumber;
        this.executedAt = executedAt;
    }
}
