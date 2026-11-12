package com.shinhan.corebank.fds.application.port.out;

import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import java.time.LocalDateTime;
import java.util.List;

/** 조사 작업의 멱등 등록과 조건부 상태 전이를 제공한다. */
public interface FdsInvestigationJobPort {
    FdsInvestigationJob createIfAbsent(long transferHoldId, String investigationId, LocalDateTime now);

    FdsInvestigationJob findByHoldId(long transferHoldId);

    FdsInvestigationJob findByInvestigationId(String investigationId);

    List<Long> findDue(LocalDateTime now, int limit);

    boolean claim(long transferHoldId, LocalDateTime now, LocalDateTime leaseExpiresAt);

    boolean retry(long transferHoldId, LocalDateTime nextAttemptAt, String reason, LocalDateTime now);

    boolean fail(long transferHoldId, String reason, LocalDateTime now);

    int recoverExpired(LocalDateTime now);

    void completeWithReport(
            long transferHoldId,
            String investigationId,
            String schemaVersion,
            String reportJson,
            String reportSha256,
            LocalDateTime now);
}
