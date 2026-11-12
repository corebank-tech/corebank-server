package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldEvidence;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이체 보류. 기표하지 않고 잡아 둔 즉시이체의 원본 요청이다(glossary 12번).
 *
 * 읽고 바꾸면 동시 처리에서 둘 다 통과하므로 상태 전이 메서드를 두지 않고 TransferHoldPort의 조건부 UPDATE로만 한다.
 */
public class TransferHold {

    // 실행 30분 전까지 취소할 수 있다(금감원 안내·은행 공통 기준, 생활법령 지연이체)
    private static final Duration CANCEL_CUTOFF = Duration.ofMinutes(30);

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
    private final Integer policyCount10m;
    private final Long policyAmount10m;
    private final Boolean policyHesitationInWindow;
    private final Boolean policyNewRecipientAtWindowStart;
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
            Integer policyCount10m,
            Long policyAmount10m,
            Boolean policyHesitationInWindow,
            Boolean policyNewRecipientAtWindowStart,
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
        this.policyCount10m = policyCount10m;
        this.policyAmount10m = policyAmount10m;
        this.policyHesitationInWindow = policyHesitationInWindow;
        this.policyNewRecipientAtWindowStart = policyNewRecipientAtWindowStart;
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
        TransferHoldEvidence evidence = decision.evidence();
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
                evidence == null ? null : evidence.count10m(),
                evidence == null ? null : evidence.amount10m(),
                evidence == null ? null : evidence.hesitationInWindow(),
                evidence == null ? null : evidence.newRecipientAtWindowStart(),
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
            Integer policyCount10m,
            Long policyAmount10m,
            Boolean policyHesitationInWindow,
            Boolean policyNewRecipientAtWindowStart,
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
                policyCount10m,
                policyAmount10m,
                policyHesitationInWindow,
                policyNewRecipientAtWindowStart,
                releaseMode,
                releaseAt,
                status,
                authenticatedAt,
                heldAt,
                transferId,
                reasonCode);
    }

    // 실행일자는 중복 기표를 막는 유니크 키의 일부라 보류한 날짜와 같아야 한다
    public boolean matchesOriginal(
            long customerId,
            long withdrawalAccountId,
            String depositAccountNumber,
            long amount,
            LocalDate executionDate) {
        return heldAt.toLocalDate().equals(executionDate)
                && this.customerId == customerId
                && this.withdrawalAccountId == withdrawalAccountId
                && this.depositAccountNumber.equals(depositAccountNumber)
                && this.amount == amount;
    }

    /** 고객이 취소할 수 있는 마지막 시각. 승인으로 푸는 보류는 시각이 없어 null이다. */
    public LocalDateTime cancellableUntil() {
        return releaseAt == null ? null : releaseAt.minus(CANCEL_CUTOFF);
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

    public Integer getPolicyCount10m() {
        return policyCount10m;
    }

    public Long getPolicyAmount10m() {
        return policyAmount10m;
    }

    public Boolean getPolicyHesitationInWindow() {
        return policyHesitationInWindow;
    }

    public Boolean getPolicyNewRecipientAtWindowStart() {
        return policyNewRecipientAtWindowStart;
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
