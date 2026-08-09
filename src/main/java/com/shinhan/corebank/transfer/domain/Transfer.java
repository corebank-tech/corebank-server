package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;

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

        if (amount <= 0) {
            throw new IllegalArgumentException(""); // 이체 금액 0 이하 안된다!
        }
        if (withdrawalAccountId.equals(depositAccountId)) {
            throw new IllegalArgumentException(""); // 이체/출금 계좌가 같을 수는 없다!
        }

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

    // 이체 실패 처리
    public void fail(String errorCode, String errorMessage) {
        this.status = ProcessResultStatus.ERROR;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

}
