package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import java.time.LocalDateTime;

/**
 * 이체 보류. 기표하지 않고 잡아 둔 즉시이체의 원본 요청이다(glossary 12번).
 *
 * 읽고 바꾸면 동시 처리에서 둘 다 통과하므로 상태 전이 메서드를 두지 않고 TransferHoldPort의 조건부 UPDATE로만 한다.
 */
public class TransferHold {

    private final Long transferHoldId;
    private final long customerId;
    private final long withdrawalAccountId;
    private final String depositAccountNumber;
    private final long amount;
    private final String myPassbookMemo;
    private final String recipientPassbookMemo;
    private final TransferChannel channel;
    private final String flowId;
    private final String policyCode;
    private final TransferHoldReleaseMode releaseMode;
    private final LocalDateTime releaseAt;
    private final TransferHoldStatus status;
    private final LocalDateTime authenticatedAt;
    private final LocalDateTime heldAt;
    private final Long transferId;
    private final String reasonCode;

    private TransferHold(
            Long transferHoldId,
            long customerId,
            long withdrawalAccountId,
            String depositAccountNumber,
            long amount,
            String myPassbookMemo,
            String recipientPassbookMemo,
            TransferChannel channel,
            String flowId,
            String policyCode,
            TransferHoldReleaseMode releaseMode,
            LocalDateTime releaseAt,
            TransferHoldStatus status,
            LocalDateTime authenticatedAt,
            LocalDateTime heldAt,
            Long transferId,
            String reasonCode) {
        this.transferHoldId = transferHoldId;
        this.customerId = customerId;
        this.withdrawalAccountId = withdrawalAccountId;
        this.depositAccountNumber = depositAccountNumber;
        this.amount = amount;
        this.myPassbookMemo = myPassbookMemo;
        this.recipientPassbookMemo = recipientPassbookMemo;
        this.channel = channel;
        this.flowId = flowId;
        this.policyCode = policyCode;
        this.releaseMode = releaseMode;
        this.releaseAt = releaseAt;
        this.status = status;
        this.authenticatedAt = authenticatedAt;
        this.heldAt = heldAt;
        this.transferId = transferId;
        this.reasonCode = reasonCode;
    }

    /** 훅 C가 보류로 판정한 요청을 HELD로 만든다. 판정 값의 조합은 TransferHoldDecision이 이미 검증했다. */
    public static TransferHold hold(
            long customerId,
            long withdrawalAccountId,
            String depositAccountNumber,
            long amount,
            String myPassbookMemo,
            String recipientPassbookMemo,
            TransferChannel channel,
            String flowId,
            TransferHoldDecision decision,
            LocalDateTime authenticatedAt,
            LocalDateTime heldAt) {
        // 바로 실행 판정으로 보류를 만들려는 건 호출 결함이다
        if (!decision.hold()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return new TransferHold(
                null,
                customerId,
                withdrawalAccountId,
                depositAccountNumber,
                amount,
                myPassbookMemo,
                recipientPassbookMemo,
                channel,
                flowId,
                decision.policyCode(),
                decision.releaseMode(),
                decision.releaseAt(),
                TransferHoldStatus.HELD,
                authenticatedAt,
                heldAt,
                null,
                null);
    }

    public static TransferHold of(
            Long transferHoldId,
            long customerId,
            long withdrawalAccountId,
            String depositAccountNumber,
            long amount,
            String myPassbookMemo,
            String recipientPassbookMemo,
            TransferChannel channel,
            String flowId,
            String policyCode,
            TransferHoldReleaseMode releaseMode,
            LocalDateTime releaseAt,
            TransferHoldStatus status,
            LocalDateTime authenticatedAt,
            LocalDateTime heldAt,
            Long transferId,
            String reasonCode) {
        return new TransferHold(
                transferHoldId,
                customerId,
                withdrawalAccountId,
                depositAccountNumber,
                amount,
                myPassbookMemo,
                recipientPassbookMemo,
                channel,
                flowId,
                policyCode,
                releaseMode,
                releaseAt,
                status,
                authenticatedAt,
                heldAt,
                transferId,
                reasonCode);
    }

    /** 실행 중으로 선점됐고, 실행하려는 요청이 저장된 원본과 같은지. 다르면 인증 없이 실행하면 안 된다. */
    public boolean isExecutableAs(long customerId, long withdrawalAccountId, String depositAccountNumber, long amount) {
        return status == TransferHoldStatus.EXECUTING
                && this.customerId == customerId
                && this.withdrawalAccountId == withdrawalAccountId
                && this.depositAccountNumber.equals(depositAccountNumber)
                && this.amount == amount;
    }

    public Long getTransferHoldId() {
        return transferHoldId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getWithdrawalAccountId() {
        return withdrawalAccountId;
    }

    public String getDepositAccountNumber() {
        return depositAccountNumber;
    }

    public long getAmount() {
        return amount;
    }

    public String getMyPassbookMemo() {
        return myPassbookMemo;
    }

    public String getRecipientPassbookMemo() {
        return recipientPassbookMemo;
    }

    public TransferChannel getChannel() {
        return channel;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public TransferHoldReleaseMode getReleaseMode() {
        return releaseMode;
    }

    public LocalDateTime getReleaseAt() {
        return releaseAt;
    }

    public TransferHoldStatus getStatus() {
        return status;
    }

    public LocalDateTime getAuthenticatedAt() {
        return authenticatedAt;
    }

    public LocalDateTime getHeldAt() {
        return heldAt;
    }

    public Long getTransferId() {
        return transferId;
    }

    /** 거절 사유 코드, 또는 FAILED일 때 실행이 거부된 오류코드 */
    public String getReasonCode() {
        return reasonCode;
    }
}
