package com.shinhan.corebank.batch.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface BatchExecutionHistoryPort {
    // 스텝 1회 실행 결과를 1행으로 남긴다(failureReason은 SUCCESS면 null) - 관측용이며 #556 재시작의 체크포인트로 보장하지 않는다.
    void record(
            String jobName,
            String stepName,
            LocalDate businessDate,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            BatchStepStatus status,
            String failureReason);
}
