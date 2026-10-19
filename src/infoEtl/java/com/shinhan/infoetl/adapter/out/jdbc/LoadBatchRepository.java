package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.domain.LoadStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
            LocalDate asOfDate)
            throws SQLException {
        try (PreparedStatement statement =
                mart.prepareStatement("UPDATE mart_load_batch SET status = ?, completed_at = ?, balance_row_count = ?,"
                        + " event_row_count = ?, mart_balance_total = ?, as_of_date = ?"
                        + " WHERE load_batch_id = ? AND status = ?")) {
            statement.setString(1, LoadStatus.COMPLETED.name());
            statement.setObject(2, completedAt);
            statement.setLong(3, balanceRowCount);
            statement.setLong(4, eventRowCount);
            statement.setLong(5, martBalanceTotal);
            statement.setObject(6, asOfDate);
            statement.setLong(7, loadBatchId);
            statement.setString(8, LoadStatus.RUNNING.name());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("RUNNING 상태가 아니어서 완료 처리하지 못했습니다. loadBatchId=" + loadBatchId);
            }
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
