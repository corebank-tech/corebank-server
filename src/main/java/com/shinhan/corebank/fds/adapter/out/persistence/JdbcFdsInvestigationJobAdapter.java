package com.shinhan.corebank.fds.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 조사 작업을 DB 조건부 갱신으로 선점하고 확정 상태를 보호한다. */
@Repository
@RequiredArgsConstructor
public class JdbcFdsInvestigationJobAdapter implements FdsInvestigationJobPort {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public FdsInvestigationJob createIfAbsent(long holdId, String investigationId, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO fds_investigation_job "
                        + "(transfer_hold_id, investigation_id, job_status, attempt_count, next_attempt_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'PENDING', 0, ?, ?, ?) ON DUPLICATE KEY UPDATE transfer_hold_id = transfer_hold_id",
                holdId,
                investigationId,
                timestamp(now),
                timestamp(now),
                timestamp(now));
        FdsInvestigationJob stored = findByHoldId(holdId);
        if (stored == null) throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        return stored;
    }

    @Override
    public FdsInvestigationJob findByHoldId(long holdId) {
        return query("transfer_hold_id", holdId);
    }

    @Override
    public FdsInvestigationJob findByInvestigationId(String investigationId) {
        return query("investigation_id", investigationId);
    }

    private FdsInvestigationJob query(String column, Object value) {
        return jdbc
                .query(
                        "SELECT transfer_hold_id, investigation_id, job_status, attempt_count, next_attempt_at, "
                                + "lease_expires_at, last_failure_reason FROM fds_investigation_job WHERE "
                                + column + " = ?",
                        (rs, row) -> new FdsInvestigationJob(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getInt(4),
                                rs.getTimestamp(5).toLocalDateTime(),
                                rs.getTimestamp(6) == null
                                        ? null
                                        : rs.getTimestamp(6).toLocalDateTime(),
                                rs.getString(7)),
                        value)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Long> findDue(LocalDateTime now, int limit) {
        return jdbc.queryForList(
                "SELECT transfer_hold_id FROM fds_investigation_job "
                        + "WHERE job_status = 'PENDING' AND next_attempt_at <= ? "
                        + "ORDER BY next_attempt_at, transfer_hold_id LIMIT ?",
                Long.class,
                timestamp(now),
                limit);
    }

    @Override
    public boolean claim(long holdId, LocalDateTime now, LocalDateTime leaseExpiresAt) {
        return jdbc.update(
                        "UPDATE fds_investigation_job SET job_status='RUNNING', attempt_count=attempt_count+1, "
                                + "lease_expires_at=?, updated_at=? WHERE transfer_hold_id=? AND job_status='PENDING' "
                                + "AND next_attempt_at<=? AND attempt_count<3",
                        timestamp(leaseExpiresAt),
                        timestamp(now),
                        holdId,
                        timestamp(now))
                == 1;
    }

    @Override
    public boolean retry(long holdId, LocalDateTime nextAttemptAt, String reason, LocalDateTime now) {
        return jdbc.update(
                        "UPDATE fds_investigation_job SET job_status='PENDING', next_attempt_at=?, lease_expires_at=NULL, "
                                + "last_failure_reason=?, updated_at=? WHERE transfer_hold_id=? AND job_status='RUNNING'",
                        timestamp(nextAttemptAt),
                        reason,
                        timestamp(now),
                        holdId)
                == 1;
    }

    @Override
    public boolean fail(long holdId, String reason, LocalDateTime now) {
        return jdbc.update(
                        "UPDATE fds_investigation_job SET job_status='FAILED', lease_expires_at=NULL, "
                                + "last_failure_reason=?, updated_at=? WHERE transfer_hold_id=? AND job_status='RUNNING'",
                        reason,
                        timestamp(now),
                        holdId)
                == 1;
    }

    @Override
    public int recoverExpired(LocalDateTime now) {
        return jdbc.update(
                "UPDATE fds_investigation_job SET job_status=CASE WHEN attempt_count>=3 THEN 'FAILED' ELSE 'PENDING' END, "
                        + "next_attempt_at=?, lease_expires_at=NULL, last_failure_reason='LEASE_EXPIRED', updated_at=? "
                        + "WHERE job_status='RUNNING' AND lease_expires_at<=?",
                timestamp(now),
                timestamp(now),
                timestamp(now));
    }

    @Override
    @Transactional
    public void recordEvidence(
            String investigationId,
            long holdId,
            long customerId,
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo,
            List<Long> refIds,
            LocalDateTime now) {
        if (rangeFrom == null
                || rangeTo == null
                || !rangeFrom.isBefore(rangeTo)
                || refIds == null
                || refIds.stream().anyMatch(refId -> refId == null || refId <= 0)) {
            throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT);
        }
        FdsInvestigationJob job = findByInvestigationId(investigationId);
        if (job == null || job.transferHoldId() != holdId) {
            throw new BusinessException(FdsErrorCode.NOT_FOUND);
        }
        for (Long refId : refIds) {
            jdbc.update(
                    "INSERT INTO fds_investigation_evidence "
                            + "(investigation_id, ref_id, transfer_hold_id, customer_id, range_from, range_to, provided_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE ref_id=ref_id",
                    investigationId,
                    refId,
                    holdId,
                    customerId,
                    timestamp(rangeFrom),
                    timestamp(rangeTo),
                    timestamp(now));
        }
    }

    @Override
    @Transactional
    public void recordHistorySnapshot(
            String investigationId,
            long holdId,
            long customerId,
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo,
            boolean hasMore,
            List<HistorySnapshotItem> items,
            LocalDateTime now) {
        recordEvidence(
                investigationId,
                holdId,
                customerId,
                rangeFrom,
                rangeTo,
                items.stream().map(HistorySnapshotItem::refId).toList(),
                now);
        jdbc.update(
                "INSERT INTO fds_investigation_history_snapshot "
                        + "(investigation_id,transfer_hold_id,customer_id,range_from,range_to,has_more,provided_at) "
                        + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE investigation_id=investigation_id",
                investigationId,
                holdId,
                customerId,
                timestamp(rangeFrom),
                timestamp(rangeTo),
                hasMore,
                timestamp(now));
        for (HistorySnapshotItem item : items) {
            jdbc.update(
                    "INSERT INTO fds_investigation_history_item "
                            + "(investigation_id,ref_id,amount,transferred_at,recipient_account_masked) VALUES (?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE ref_id=ref_id",
                    investigationId,
                    item.refId(),
                    item.amount(),
                    timestamp(item.transferredAt()),
                    item.recipientAccountMasked());
        }
    }

    @Override
    @Transactional
    public void recordRecipientSnapshot(
            String investigationId,
            long holdId,
            long customerId,
            LocalDateTime firstSucceededAt,
            boolean hasSucceededBeforeWindow,
            int otherWithdrawalAccountCount,
            LocalDateTime now) {
        FdsInvestigationJob job = findByInvestigationId(investigationId);
        if (job == null || job.transferHoldId() != holdId || otherWithdrawalAccountCount < 0) {
            throw new BusinessException(FdsErrorCode.NOT_FOUND);
        }
        jdbc.update(
                "INSERT INTO fds_investigation_recipient_snapshot "
                        + "(investigation_id,transfer_hold_id,customer_id,first_succeeded_at,succeeded_before_window,"
                        + "other_withdrawal_account_count,provided_at) VALUES (?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE investigation_id=investigation_id",
                investigationId,
                holdId,
                customerId,
                firstSucceededAt == null ? null : timestamp(firstSucceededAt),
                hasSucceededBeforeWindow,
                otherWithdrawalAccountCount,
                timestamp(now));
    }

    @Override
    public Set<Long> findEvidenceIds(String investigationId) {
        return jdbc
                .queryForList(
                        "SELECT ref_id FROM fds_investigation_evidence WHERE investigation_id=?",
                        Long.class,
                        investigationId)
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public void completeWithReport(
            long holdId, String investigationId, String schemaVersion, String json, String hash, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO fds_investigation_report "
                        + "(investigation_id, transfer_hold_id, schema_version, report_json, report_sha256, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE investigation_id=investigation_id",
                investigationId,
                holdId,
                schemaVersion,
                json,
                hash,
                timestamp(now));
        String storedHash = jdbc.queryForObject(
                "SELECT report_sha256 FROM fds_investigation_report WHERE investigation_id=?",
                String.class,
                investigationId);
        if (!hash.equals(storedHash)) throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        int updated = jdbc.update(
                "UPDATE fds_investigation_job SET job_status='COMPLETED', lease_expires_at=NULL, updated_at=? "
                        + "WHERE transfer_hold_id=? AND investigation_id=? AND job_status IN ('RUNNING','COMPLETED')",
                timestamp(now),
                holdId,
                investigationId);
        if (updated != 1) throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}
