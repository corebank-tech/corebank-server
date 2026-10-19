package com.shinhan.infoetl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class LoadBatchRepository {

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

    public void markCompleted(
            Connection mart, long loadBatchId, LocalDateTime completedAt, long balanceRowCount, long martBalanceTotal)
            throws SQLException {
        try (PreparedStatement statement =
                mart.prepareStatement("UPDATE mart_load_batch SET status = ?, completed_at = ?, balance_row_count = ?,"
                        + " mart_balance_total = ? WHERE load_batch_id = ?")) {
            statement.setString(1, LoadStatus.COMPLETED.name());
            statement.setObject(2, completedAt);
            statement.setLong(3, balanceRowCount);
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
