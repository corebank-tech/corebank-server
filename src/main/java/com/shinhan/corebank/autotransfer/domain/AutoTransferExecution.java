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

    // 외부 이체 호출 전에 먼저 저장한다. (auto_transfer_id, execution_date) 유니크 제약이
    // 이 저장 시점의 멱등성 체크포인트 역할을 한다 — 배치가 재실행돼도 이미 PROCESSING 행이
    // 있으면 중복 INSERT가 막혀 동일 이체가 다시 실행되지 않는다.
    public static AutoTransferExecution processing(LocalDate executionDate, Long amount, LocalDateTime executedAt) {
        AutoTransferExecution e = new AutoTransferExecution();
        e.executionDate = executionDate;
        e.amount = amount;
        e.status = ProcessResultStatus.PROCESSING;
        e.executedAt = executedAt;
        return e;
    }

    // PROCESSING -> SUCCESS
    public void markSuccess(String transactionNumber) {
        if (this.status != ProcessResultStatus.PROCESSING) {
            throw new IllegalStateException("처리중 상태에서만 성공으로 전환할 수 있습니다.");
        }
        this.status = ProcessResultStatus.SUCCESS;
        this.transactionNumber = transactionNumber;
    }

    // PROCESSING -> ERROR
    public void markError(String failureReason) {
        if (this.status != ProcessResultStatus.PROCESSING) {
            throw new IllegalStateException("처리중 상태에서만 실패로 전환할 수 있습니다.");
        }
        this.status = ProcessResultStatus.ERROR;
        this.failureReason = failureReason;
    }

}
