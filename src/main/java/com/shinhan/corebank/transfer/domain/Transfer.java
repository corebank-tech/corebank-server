package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Transfer {

    private Long transferId;
    private String transactionNumber;
    private Long withdrawalAccountId;
    private Long depositAccountId;
    private String depositAccountNumber;
    private String payeeName;
    private long amount;
    private long fee;
    private TransferType transferType;
    private TransferChannel channel;
    private ProcessResultStatus status;
    private TransferSourceType sourceType;
    private Long sourceId;
    private String myPassbookMemo;
    private String recipientPassbookMemo;
    private Long withdrawalBalanceAfter;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime transferredAt;
    private LocalDateTime createdAt;


    /**
     * 신규 이체 도메인 생성 팩토리
     */
    public static Transfer create(
            String transactionNumber,
            Long withdrawalAccountId,
            Long depositAccountId,
            String depositAccountNumber,
            String payeeName,
            long amount,
            long fee,
            TransferType transferType,
            TransferChannel channel,
            TransferSourceType sourceType,
            Long sourceId,
            String myPassbookMemo,
            String recipientPassbookMemo,
            LocalDateTime now
    ) {
        TransferValidations.requireAccountIdsPresent(withdrawalAccountId, depositAccountId);
        TransferValidations.requireNonBlank(transactionNumber, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(depositAccountNumber, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(payeeName, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(transferType, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(channel, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(now, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requirePositiveAmount(amount);
        TransferValidations.requireDifferentAccounts(withdrawalAccountId, depositAccountId);

        return Transfer.builder()
                .transactionNumber(transactionNumber)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountId(depositAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName(payeeName)
                .amount(amount)
                .fee(fee)
                .transferType(transferType)
                .channel(channel)
                .status(ProcessResultStatus.PROCESSING)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .transferredAt(now)
                .createdAt(now)
                .build();
    }

    // 이체 완료 처리
    public void complete(long withdrawalBalanceAfter) {
        this.status = ProcessResultStatus.SUCCESS;
        this.withdrawalBalanceAfter = withdrawalBalanceAfter;
    }

    // 이체 완료 처리 (완료 시각 지정)
    public void complete(long withdrawalBalanceAfter, LocalDateTime completedAt) {
        this.status = ProcessResultStatus.SUCCESS;
        this.withdrawalBalanceAfter = withdrawalBalanceAfter;
        if (completedAt != null) {
            this.transferredAt = completedAt;
        }
    }

    // 이체 실패 처리
    public void fail(String errorCode, String errorMessage) {
        this.status = ProcessResultStatus.ERROR;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}
