package com.shinhan.corebank.paymentstop.domain;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

/** 지급정지 사건. 피해자의 피해구제 신청으로 계좌 하나에 건다(법 제4조). */
@Getter
public class PaymentStopCase {

    private final Long caseId;
    private final long accountId;
    // 다툼 거래 송금인의 당행 계좌. 타행이거나 송금인이 여럿이면 null
    private final Long senderAccountId;
    private final LocalDate objectionDeadline;
    private CaseStatus status;
    private final long registeredBy;
    private final List<DisputedTransfer> disputedTransfers;

    private PaymentStopCase(
            Long caseId,
            long accountId,
            Long senderAccountId,
            LocalDate objectionDeadline,
            CaseStatus status,
            long registeredBy,
            List<DisputedTransfer> disputedTransfers) {
        if (objectionDeadline == null || status == null) {
            throw new IllegalArgumentException("신청 기한과 상태는 필수입니다.");
        }
        if (disputedTransfers == null || disputedTransfers.isEmpty()) {
            throw new IllegalArgumentException("다툼 거래가 하나 이상 있어야 합니다.");
        }
        this.caseId = caseId;
        this.accountId = accountId;
        this.senderAccountId = senderAccountId;
        this.objectionDeadline = objectionDeadline;
        this.status = status;
        this.registeredBy = registeredBy;
        this.disputedTransfers = List.copyOf(disputedTransfers);
    }

    public static PaymentStopCase register(
            long accountId,
            Long senderAccountId,
            LocalDate objectionDeadline,
            long registeredBy,
            List<DisputedTransfer> disputedTransfers) {
        return new PaymentStopCase(
                null,
                accountId,
                senderAccountId,
                objectionDeadline,
                CaseStatus.STOPPED,
                registeredBy,
                disputedTransfers);
    }

    public static PaymentStopCase reconstitute(
            Long caseId,
            long accountId,
            Long senderAccountId,
            LocalDate objectionDeadline,
            CaseStatus status,
            long registeredBy,
            List<DisputedTransfer> disputedTransfers) {
        return new PaymentStopCase(
                caseId, accountId, senderAccountId, objectionDeadline, status, registeredBy, disputedTransfers);
    }

    public long disputedTotal() {
        return disputedTransfers.stream().mapToLong(DisputedTransfer::amount).sum();
    }

    public void reflect(DecisionResult result) {
        this.status = switch (result) {
            case ACCEPTED -> CaseStatus.RELEASED;
            case PARTIAL -> CaseStatus.PARTIAL;
            case REJECTED -> status;
        };
    }
}
