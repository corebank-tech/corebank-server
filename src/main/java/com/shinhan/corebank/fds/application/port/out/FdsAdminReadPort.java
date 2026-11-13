package com.shinhan.corebank.fds.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 목록·상세가 조사 당시 저장된 P6 스냅샷만 읽는 계약이다. */
public interface FdsAdminReadPort {
    List<Long> findHoldIds();

    Report findReport(String investigationId);

    ToolResults findToolResults(String investigationId);

    List<CallRecord> findCallRecords(long transferHoldId);

    AdminAction findLatestAction(long transferHoldId);

    record Report(String schemaVersion, String reportJson, LocalDateTime completedAt) {}

    record ToolResults(History history, Recipient recipient) {}

    record History(
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo,
            boolean hasMore,
            LocalDateTime providedAt,
            List<HistoryItem> items) {}

    record HistoryItem(long refId, long amount, LocalDateTime transferredAt, String recipientAccountMasked) {}

    record Recipient(
            LocalDateTime firstSucceededAt,
            boolean hasSucceededBeforeWindow,
            int otherWithdrawalAccountCount,
            LocalDateTime providedAt) {}

    record CallRecord(
            long callRecordId,
            String callResult,
            long adminId,
            String investigationReason,
            LocalDateTime recordedAt,
            boolean selectedByAction) {}

    record AdminAction(
            String actionId,
            String actionType,
            String actionStatus,
            Long actorId,
            String reasonCode,
            Long callRecordId,
            String riskStatus,
            Long riskAccountId,
            String datasetVersion,
            String p4Status,
            Long p4ActorId,
            String p4ReasonCode,
            String failureReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
}
