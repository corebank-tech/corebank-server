package com.shinhan.corebank.autotransfer.domain;

import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.common.domain.ProcessResultStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AutoTransferExecution {
    private Long executionId;
    private LocalDate executionDate;
    private Long amount;
    private ProcessResultStatus status;
    private String transactionNumber;   // 성공 시에만. 실패는 null
    private String failureReason;   // 실패 시에만. 성공은 null
    private LocalDateTime executedAt;   // 실제 실행 시각, 예정일과 다를 수 있음

}
