package com.shinhan.corebank.account.domain;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 계좌 하나에 걸린 사건 하나의 지급정지 기록.
 *
 * <p>계좌의 상태와 보류액은 이 기록들로 계산한다. 한 계좌에 여러 사건이 겹칠 수 있고, 마지막 기록이 해제되면 정지 전 상태로
 * 돌아간다. 같은 계좌에 유효한 기록이 여럿이면 모두 같은 정지 전 상태를 가진다.
 */
@Getter
public class AccountPaymentStop {

    private final Long paymentStopId;
    private final long accountId;
    private final long caseId;
    private PaymentStopType stopType;
    private long requestedAmount;
    private long appliedAmount;
    private final AccountStatus statusBefore;
    private boolean active;
    private final LocalDateTime createdAt;
    private LocalDateTime releasedAt;

    private AccountPaymentStop(
            Long paymentStopId,
            long accountId,
            long caseId,
            PaymentStopType stopType,
            long requestedAmount,
            long appliedAmount,
            AccountStatus statusBefore,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime releasedAt) {
        this.paymentStopId = paymentStopId;
        this.accountId = accountId;
        this.caseId = caseId;
        this.stopType = stopType;
        this.requestedAmount = requestedAmount;
        this.appliedAmount = appliedAmount;
        this.statusBefore = statusBefore;
        this.active = active;
        this.createdAt = createdAt;
        this.releasedAt = releasedAt;

        validate();
    }

    public static AccountPaymentStop full(
            long accountId, long caseId, AccountStatus statusBefore, LocalDateTime createdAt) {
        return new AccountPaymentStop(
                null, accountId, caseId, PaymentStopType.FULL, 0L, 0L, statusBefore, true, createdAt, null);
    }

    public static AccountPaymentStop reconstitute(
            Long paymentStopId,
            long accountId,
            long caseId,
            PaymentStopType stopType,
            long requestedAmount,
            long appliedAmount,
            AccountStatus statusBefore,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime releasedAt) {
        return new AccountPaymentStop(
                paymentStopId,
                accountId,
                caseId,
                stopType,
                requestedAmount,
                appliedAmount,
                statusBefore,
                active,
                createdAt,
                releasedAt);
    }

    /** 전부 지급정지를 일부 지급정지로 바꾼다. 적용액은 호출자가 남은 잔액으로 잘라서 넘긴다. */
    public void convertToPartial(long requestedAmount, long appliedAmount) {
        if (!active || stopType != PaymentStopType.FULL) {
            throw new IllegalStateException("유효한 전부 지급정지만 일부 지급정지로 바꿀 수 있습니다.");
        }
        if (requestedAmount <= 0 || appliedAmount < 0 || appliedAmount > requestedAmount) {
            throw new IllegalArgumentException("적용액은 0 이상이고 요청액 이하여야 합니다.");
        }

        this.stopType = PaymentStopType.PARTIAL;
        this.requestedAmount = requestedAmount;
        this.appliedAmount = appliedAmount;
    }

    public void release(LocalDateTime releasedAt) {
        if (!active) {
            throw new IllegalStateException("이미 해제된 지급정지입니다.");
        }
        if (releasedAt == null) {
            throw new IllegalArgumentException("해제 시각은 필수입니다.");
        }

        this.active = false;
        this.releasedAt = releasedAt;
    }

    public boolean isFull() {
        return stopType == PaymentStopType.FULL;
    }

    private void validate() {
        if (accountId <= 0 || caseId <= 0) {
            throw new IllegalArgumentException("계좌 ID와 사건 ID는 양수여야 합니다.");
        }
        if (stopType == null || createdAt == null) {
            throw new IllegalArgumentException("지급정지 종류와 생성 시각은 필수입니다.");
        }
        if (statusBefore != AccountStatus.ACTIVE && statusBefore != AccountStatus.SUSPENDED) {
            throw new IllegalArgumentException("정지 전 상태는 ACTIVE 또는 SUSPENDED여야 합니다.");
        }
        if (stopType == PaymentStopType.FULL && (requestedAmount != 0 || appliedAmount != 0)) {
            throw new IllegalArgumentException("전부 지급정지는 금액을 갖지 않습니다.");
        }
        if (stopType == PaymentStopType.PARTIAL
                && (requestedAmount <= 0 || appliedAmount < 0 || appliedAmount > requestedAmount)) {
            throw new IllegalArgumentException("일부 지급정지의 적용액은 0 이상이고 요청액 이하여야 합니다.");
        }
        if (active == (releasedAt != null)) {
            throw new IllegalArgumentException("유효한 지급정지는 해제 시각이 없고, 해제된 지급정지는 해제 시각이 있어야 합니다.");
        }
    }
}
