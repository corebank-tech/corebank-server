package com.shinhan.corebank.fds.application.port.out;

import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 조사 작업의 멱등 등록과 조건부 상태 전이를 제공한다. */
public interface FdsInvestigationJobPort {
    FdsInvestigationJob createIfAbsent(long transferHoldId, String investigationId, LocalDateTime now);

    FdsInvestigationJob findByHoldId(long transferHoldId);

    FdsInvestigationJob findByInvestigationId(String investigationId);

    List<Long> findDue(LocalDateTime now, int limit);

    Integer claim(long transferHoldId, LocalDateTime now, LocalDateTime leaseExpiresAt);

    boolean retry(long transferHoldId, int attemptNo, LocalDateTime nextAttemptAt, String reason, LocalDateTime now);

    boolean fail(long transferHoldId, int attemptNo, String reason, LocalDateTime now);

    int recoverExpired(LocalDateTime now);

    void recordEvidence(
            String investigationId,
            long transferHoldId,
            long customerId,
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo,
            List<Long> refIds,
            LocalDateTime now);

    void recordHistorySnapshot(
            String investigationId,
            long transferHoldId,
            int attemptNo,
            long customerId,
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo,
            boolean hasMore,
            List<HistorySnapshotItem> items,
            LocalDateTime now);

    void recordRecipientSnapshot(
            String investigationId,
            long transferHoldId,
            int attemptNo,
            long customerId,
            LocalDateTime firstSucceededAt,
            boolean hasSucceededBeforeWindow,
            int otherWithdrawalAccountCount,
            LocalDateTime now);

    Set<Long> findEvidenceIds(String investigationId);

    boolean hasReport(String investigationId);

    void completeWithReport(
            long transferHoldId,
            String investigationId,
            int attemptNo,
            String schemaVersion,
            String reportJson,
            String reportSha256,
            LocalDateTime now);

    record HistorySnapshotItem(long refId, long amount, LocalDateTime transferredAt, String recipientAccountMasked) {}
}
