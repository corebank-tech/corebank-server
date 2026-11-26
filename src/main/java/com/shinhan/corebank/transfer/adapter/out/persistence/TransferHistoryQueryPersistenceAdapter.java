package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryStatus;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryQueryPort;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryRecord;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class TransferHistoryQueryPersistenceAdapter implements TransferHistoryQueryPort {

    private static final String UNIFIED_SELECT =
            """
            SELECT
                CASE WHEN t.source_type = 'HELD'
                     THEN CONCAT('HOLD:', t.source_id)
                     ELSE CONCAT('TRANSFER:', t.transaction_number) END AS history_id,
                CASE WHEN t.source_type = 'HELD' THEN t.source_id ELSE NULL END AS transfer_hold_id,
                t.transaction_number,
                t.status AS history_status,
                t.transferred_at AS occurred_at,
                t.withdrawal_account_id,
                t.deposit_account_number,
                t.payee_name,
                t.amount,
                t.transfer_type,
                t.channel,
                t.error_code,
                t.error_message,
                t.transfer_id AS sort_id
              FROM transfer t
             WHERE t.withdrawal_account_id = :withdrawalAccountId
               AND t.transferred_at >= :fromAt
               AND t.transferred_at < :toAt
            %s
            UNION ALL
            SELECT
                CONCAT('HOLD:', h.transfer_hold_id) AS history_id,
                h.transfer_hold_id,
                NULL AS transaction_number,
                CASE h.status
                    WHEN 'HELD' THEN 'HELD'
                    WHEN 'REJECTED' THEN 'REJECTED'
                    WHEN 'FAILED' THEN 'ERROR'
                    WHEN 'EXECUTING' THEN 'HELD'
                    WHEN 'EXECUTED' THEN 'SUCCESS'
                END AS history_status,
                h.held_at AS occurred_at,
                h.withdrawal_account_id,
                h.deposit_account_number,
                NULL AS payee_name,
                h.amount,
                'IMMEDIATE' AS transfer_type,
                h.channel,
                CASE WHEN h.status = 'FAILED' THEN h.reason_code ELSE NULL END AS error_code,
                NULL AS error_message,
                h.transfer_hold_id AS sort_id
              FROM transfer_hold h
             WHERE h.withdrawal_account_id = :withdrawalAccountId
               AND h.held_at >= :fromAt
               AND h.held_at < :toAt
               AND h.status IN ('HELD', 'REJECTED', 'FAILED', 'EXECUTING', 'EXECUTED')
               AND NOT EXISTS (
                    SELECT 1 FROM transfer linked
                     WHERE linked.source_type = 'HELD'
                       AND linked.source_id = h.transfer_hold_id
               )
            %s
            """;

    private final EntityManager entityManager;

    public TransferHistoryQueryPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<TransferHistoryRecord> search(
            Long withdrawalAccountId,
            TransferHistoryStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            TransferHistorySort sort,
            Pageable pageable) {
        String union = unifiedSelect(status);
        String direction = sort == TransferHistorySort.OLDEST ? "ASC" : "DESC";
        Query contentQuery =
                entityManager.createNativeQuery("SELECT * FROM (" + union + ") history ORDER BY occurred_at "
                        + direction + ", sort_id " + direction + ", history_id " + direction);
        bind(contentQuery, withdrawalAccountId, status, fromDate, toDate);
        if (pageable.isPaged()) {
            contentQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
            contentQuery.setMaxResults(pageable.getPageSize());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = contentQuery.getResultList();
        List<TransferHistoryRecord> content = rows.stream().map(this::toRecord).toList();

        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + union + ") history");
        bind(countQuery, withdrawalAccountId, status, fromDate, toDate);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public TransferHistoryAggregate summarize(
            Long withdrawalAccountId, TransferHistoryStatus status, LocalDate fromDate, LocalDate toDate) {
        String union = unifiedSelect(status);
        Query query = entityManager.createNativeQuery(
                """
                SELECT history_status, COUNT(*), COALESCE(SUM(amount), 0)
                  FROM (%s) history
                 WHERE history_status IN ('SUCCESS', 'ERROR')
                 GROUP BY history_status
                """
                        .formatted(union));
        bind(query, withdrawalAccountId, status, fromDate, toDate);

        long successCount = 0L, successAmount = 0L, errorCount = 0L, errorAmount = 0L;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        for (Object[] row : rows) {
            String rowStatus = (String) row[0];
            long count = ((Number) row[1]).longValue();
            long amount = ((Number) row[2]).longValue();
            if ("SUCCESS".equals(rowStatus)) {
                successCount = count;
                successAmount = amount;
            } else {
                errorCount = count;
                errorAmount = amount;
            }
        }
        return new TransferHistoryAggregate(successCount, successAmount, errorCount, errorAmount);
    }

    private String unifiedSelect(TransferHistoryStatus status) {
        String transferStatus = status == null ? "" : " AND t.status = :status";
        String holdStatus = status == null
                ? ""
                : " AND (CASE h.status WHEN 'HELD' THEN 'HELD' WHEN 'REJECTED' THEN 'REJECTED' "
                        + "WHEN 'FAILED' THEN 'ERROR' WHEN 'EXECUTING' THEN 'HELD' "
                        + "WHEN 'EXECUTED' THEN 'SUCCESS' END) = :status";
        return UNIFIED_SELECT.formatted(transferStatus, holdStatus);
    }

    private void bind(
            Query query, Long withdrawalAccountId, TransferHistoryStatus status, LocalDate fromDate, LocalDate toDate) {
        query.setParameter("withdrawalAccountId", withdrawalAccountId);
        query.setParameter("fromAt", fromDate.atStartOfDay());
        query.setParameter("toAt", toDate.plusDays(1).atStartOfDay());
        if (status != null) {
            query.setParameter("status", status.name());
        }
    }

    private TransferHistoryRecord toRecord(Object[] row) {
        return new TransferHistoryRecord(
                (String) row[0],
                numberAsLong(row[1]),
                (String) row[2],
                TransferHistoryStatus.valueOf((String) row[3]),
                asLocalDateTime(row[4]),
                numberAsLong(row[5]),
                (String) row[6],
                (String) row[7],
                ((Number) row[8]).longValue(),
                TransferType.valueOf((String) row[9]),
                TransferChannel.valueOf((String) row[10]),
                (String) row[11],
                (String) row[12]);
    }

    private static Long numberAsLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }
}
