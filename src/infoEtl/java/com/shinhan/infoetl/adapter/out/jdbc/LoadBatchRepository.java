package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.domain.LoadStatus;
import com.shinhan.infoetl.domain.Reconciliation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

final class LoadBatchRepository {

    private static final int FAILURE_REASON_MAX_LENGTH = 200;

    public long start(Connection mart, LocalDate baseDate, LocalDateTime startedAt) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "INSERT INTO mart_load_batch (base_date, status, started_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, baseDate);
            statement.setString(2, LoadStatus.RUNNING.name());
            statement.setObject(3, startedAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    // RUNNING인 배치만 완료로 바꾼다. 이미 FAILED로 정리된 배치가 늦게 끝나도 COMPLETED로 덮지 않는다.
    public void markCompleted(
            Connection mart,
            long loadBatchId,
            LocalDateTime completedAt,
            long balanceRowCount,
            long eventRowCount,
            long martBalanceTotal,
            LocalDate asOfDate,
            Reconciliation reconciliation)
            throws SQLException {
        try (PreparedStatement statement =
                mart.prepareStatement("UPDATE mart_load_batch SET status = ?, completed_at = ?, balance_row_count = ?,"
                        + " event_row_count = ?, mart_balance_total = ?, as_of_date = ?, ledger_total = ?,"
                        + " gl_compare_from_date = ?, mart_balance_movement = ?, gl_deposit_movement = ?"
                        + " WHERE load_batch_id = ? AND status = ?")) {
            statement.setString(1, LoadStatus.COMPLETED.name());
            statement.setObject(2, completedAt);
            statement.setLong(3, balanceRowCount);
            statement.setLong(4, eventRowCount);
            statement.setLong(5, martBalanceTotal);
            setNullable(statement, 6, asOfDate, Types.DATE);
            statement.setLong(7, reconciliation.ledgerTotal());
            setNullable(statement, 8, reconciliation.glCompareFromDate(), Types.DATE);
            setNullable(statement, 9, reconciliation.martBalanceMovement(), Types.BIGINT);
            setNullable(statement, 10, reconciliation.glDepositMovement(), Types.BIGINT);
            statement.setLong(11, loadBatchId);
            statement.setString(12, LoadStatus.RUNNING.name());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("RUNNING 상태가 아니어서 완료 처리하지 못했습니다. loadBatchId=" + loadBatchId);
            }
        }
    }

    private static void setNullable(PreparedStatement statement, int index, Object value, int sqlType)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, sqlType);
        } else {
            statement.setObject(index, value);
        }
    }

    // base_date보다 앞선 가장 최근 COMPLETED 기준일(GL 대조의 D′). 없으면 null.
    public LocalDate previousCompletedDate(Connection mart, LocalDate baseDate) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "SELECT MAX(base_date) FROM mart_load_batch WHERE status = ? AND base_date < ?")) {
            statement.setString(1, LoadStatus.COMPLETED.name());
            statement.setObject(2, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject(1, LocalDate.class);
            }
        }
    }

    // 해당 기준일의 COMPLETED 배치가 남긴 마트 잔액 합계. 같은 날짜에 여러 번 적재됐으면 가장 나중 것.
    public long completedBalanceTotal(Connection mart, LocalDate baseDate) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "SELECT mart_balance_total FROM mart_load_batch WHERE status = ? AND base_date = ?"
                        + " ORDER BY load_batch_id DESC LIMIT 1")) {
            statement.setString(1, LoadStatus.COMPLETED.name());
            statement.setObject(2, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    public boolean isCompleted(Connection mart, LocalDate baseDate) throws SQLException {
        try (PreparedStatement statement =
                mart.prepareStatement("SELECT 1 FROM mart_load_batch WHERE base_date = ? AND status = ? LIMIT 1")) {
            statement.setObject(1, baseDate);
            statement.setString(2, LoadStatus.COMPLETED.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    // 상태와 무관하게 마트에 처음 기록된 기준일. asOf 연속성의 시작점이다.
    public LocalDate firstBaseDate(Connection mart) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement("SELECT MIN(base_date) FROM mart_load_batch");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getObject(1, LocalDate.class);
        }
    }

    // 이미 COMPLETED인 모든 기준일. asOf 연속성은 이 집합으로 판정한다.
    public Set<LocalDate> completedBaseDates(Connection mart) throws SQLException {
        Set<LocalDate> dates = new HashSet<>();
        try (PreparedStatement statement =
                mart.prepareStatement("SELECT DISTINCT base_date FROM mart_load_batch WHERE status = ?")) {
            statement.setString(1, LoadStatus.COMPLETED.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dates.add(resultSet.getObject(1, LocalDate.class));
                }
            }
        }
        return dates;
    }

    // 원장 대조 실패: 실패 사유와 함께 두 합계를 남긴다.
    public void markLedgerMismatch(
            Connection mart, long loadBatchId, String reason, long ledgerTotal, long martBalanceTotal)
            throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "UPDATE mart_load_batch SET status = ?, failure_reason = ?, ledger_total = ?, mart_balance_total = ?"
                        + " WHERE load_batch_id = ?")) {
            statement.setString(1, LoadStatus.FAILED.name());
            statement.setString(2, truncate(reason));
            statement.setLong(3, ledgerTotal);
            statement.setLong(4, martBalanceTotal);
            statement.setLong(5, loadBatchId);
            statement.executeUpdate();
        }
    }

    public void markFailed(Connection mart, long loadBatchId, String reason) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "UPDATE mart_load_batch SET status = ?, failure_reason = ? WHERE load_batch_id = ?")) {
            statement.setString(1, LoadStatus.FAILED.name());
            statement.setString(2, truncate(reason));
            statement.setLong(3, loadBatchId);
            statement.executeUpdate();
        }
    }

    // 같은 기준일의 이전 COMPLETED 배치를 REPLACED로 돌린다. asOf 조회는 COMPLETED만 보기 때문이다.
    public int replacePrevious(Connection mart, LocalDate baseDate, long currentLoadBatchId) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "UPDATE mart_load_batch SET status = ? WHERE base_date = ? AND status = ? AND load_batch_id <> ?")) {
            statement.setString(1, LoadStatus.REPLACED.name());
            statement.setObject(2, baseDate);
            statement.setString(3, LoadStatus.COMPLETED.name());
            statement.setLong(4, currentLoadBatchId);
            return statement.executeUpdate();
        }
    }

    // 오래 RUNNING으로 남은 행은 프로세스가 비정상 종료한 것이므로 FAILED로 돌린다.
    public int failStaleRunning(Connection mart, LocalDateTime startedBefore, String reason) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(
                "UPDATE mart_load_batch SET status = ?, failure_reason = ? WHERE status = ? AND started_at < ?")) {
            statement.setString(1, LoadStatus.FAILED.name());
            statement.setString(2, truncate(reason));
            statement.setString(3, LoadStatus.RUNNING.name());
            statement.setObject(4, startedBefore);
            return statement.executeUpdate();
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= FAILURE_REASON_MAX_LENGTH ? reason : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
