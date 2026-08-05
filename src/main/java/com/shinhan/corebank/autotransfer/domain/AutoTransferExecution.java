package com.shinhan.corebank.autotransfer.domain;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class AutoTransferExecution {
    private Long executionId;
    private LocalDate executionDate;
    private Long amount;
    private ProcessResultStatus status;
    private String transactionNumber;   // 성공 시에만. 그 외는 null
    private String failureReason;   // 실패 시에만. 그 외는 null
    private LocalDateTime executedAt;   // 실제 실행 시각, 예정일과 다를 수 있음

    // 기본 success(), error() -> processing() 이유 : 재실행될 때 이체가 중복 실행될 위험이 있어
    // processing행을 먼저 저장하면 멱등성 체크포인트 역할을 하게됨
    public static AutoTransferExecution processing(LocalDate executionDate, Long amount, LocalDateTime executedAt) {
        AutoTransferExecution e = new AutoTransferExecution();
        e.executionDate = executionDate;
        e.amount = amount;
        e.status = ProcessResultStatus.PROCESSING;
        e.executedAt = executedAt;
        return e;
    }

    // PROCESSING -> SUCCESS. 거래번호 없이는 성공으로 확정할 수 없다 — 상태를 바꾸기 전에 검증한다.
    public void markSuccess(String transactionNumber) {
        if (this.status != ProcessResultStatus.PROCESSING) {
            throw new IllegalStateException("처리중 상태에서만 성공으로 전환할 수 있습니다.");
        }
        if (transactionNumber == null || transactionNumber.isBlank()) {
            throw new IllegalArgumentException("거래번호가 없으면 성공으로 확정할 수 없습니다.");
        }
        this.status = ProcessResultStatus.SUCCESS;
        this.transactionNumber = transactionNumber;
    }

    // PROCESSING -> ERROR. 실패 사유 없이는 실패로 확정할 수 없다 — 상태를 바꾸기 전에 검증한다.
    public void markError(String failureReason) {
        if (this.status != ProcessResultStatus.PROCESSING) {
            throw new IllegalStateException("처리중 상태에서만 실패로 전환할 수 있습니다.");
        }
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("실패 사유가 없으면 실패로 확정할 수 없습니다.");
        }
        this.status = ProcessResultStatus.ERROR;
        this.failureReason = failureReason;
    }

}
