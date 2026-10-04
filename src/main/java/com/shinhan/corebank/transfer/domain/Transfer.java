package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

// PROCESSING은 커밋되지 않는다(#377). INSERT부터 complete()/fail()까지가 한 트랜잭션이라
// 커밋된 transfer 행은 항상 SUCCESS 아니면 ERROR다 — 이중 전이를 막는 내부 상태일 뿐이다.
// 회차 테이블(auto_transfer_execution 등)의 PROCESSING은 별개로 실제 커밋된다(#365).
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
    private LocalDate executionDate;
    private String myPassbookMemo;
    private String recipientPassbookMemo;
    private Long withdrawalBalanceAfter;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime transferredAt;
    private LocalDateTime createdAt;
    // 정정 체인(PH-80). 무효화는 status와 별개 — 성공했던 이력을 지우지 않고 무효가 된 시각만 더한다.
    private LocalDateTime invalidatedAt;
    private Long refTransferId;
    private CorrectionType correctionType;

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
            LocalDate executionDate,
            String myPassbookMemo,
            String recipientPassbookMemo,
            LocalDateTime now) {
        TransferValidations.requireAccountIdsPresent(withdrawalAccountId, depositAccountId);
        TransferValidations.requireNonBlank(transactionNumber, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(depositAccountNumber, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(payeeName, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(transferType, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(channel, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(now, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requirePositiveAmount(amount);
        TransferValidations.requireNonNegativeFee(fee);
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
                .executionDate(executionDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .transferredAt(now)
                .createdAt(now)
                .build();
    }

    // 이체 완료 처리 (완료 시각으로 transferredAt 갱신)
    public void complete(long withdrawalBalanceAfter, LocalDateTime completedAt) {
        requireProcessing();
        TransferValidations.requireNonNull(completedAt, CommonErrorCode.REQUIRED_FIELD_MISSING);
        this.status = ProcessResultStatus.SUCCESS;
        this.withdrawalBalanceAfter = withdrawalBalanceAfter;
        this.transferredAt = completedAt;
    }

    // 이체 실패 처리
    public void fail(String errorCode, String errorMessage) {
        requireProcessing();
        this.status = ProcessResultStatus.ERROR;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // 정정 체인의 원거래로 무효화한다. 돈이 움직인 확정 거래만 대상이고, 취소의 취소와 재무효화는 막는다.
    public void invalidate(LocalDateTime at) {
        TransferValidations.requireNonNull(at, CommonErrorCode.REQUIRED_FIELD_MISSING);
        if (this.status != ProcessResultStatus.SUCCESS
                || isInvalidated()
                || this.correctionType == CorrectionType.REVERSAL) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }
        this.invalidatedAt = at;
    }

    // 새로 만든 취소정정·정상거래에 원거래를 연결한다. 확정된 거래의 출신은 바꿀 수 없다.
    public void linkToOriginal(CorrectionType type, Long originalTransferId) {
        TransferValidations.requireNonNull(type, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(originalTransferId, CommonErrorCode.REQUIRED_FIELD_MISSING);
        if (this.status != ProcessResultStatus.PROCESSING || this.refTransferId != null) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }
        this.refTransferId = originalTransferId;
        this.correctionType = type;
    }

    public boolean isInvalidated() {
        return this.invalidatedAt != null;
    }

    // 커밋 전 과도 상태(PROCESSING)에서만 확정으로 전이 가능. 이미 확정된 이체는 재변경 금지.
    private void requireProcessing() {
        if (this.status != ProcessResultStatus.PROCESSING) {
            throw new BusinessException(TransferErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
