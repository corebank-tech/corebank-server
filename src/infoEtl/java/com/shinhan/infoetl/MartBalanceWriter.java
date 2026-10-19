package com.shinhan.infoetl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

public final class MartBalanceWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL =
            "INSERT INTO mart_daily_account_balance (base_date, account_key, customer_key, account_type,"
                    + " product_id, product_group, balance, load_batch_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public int deleteByBaseDate(Connection mart, LocalDate baseDate) throws SQLException {
        try (PreparedStatement statement =
                mart.prepareStatement("DELETE FROM mart_daily_account_balance WHERE base_date = ?")) {
            statement.setObject(1, baseDate);
            return statement.executeUpdate();
        }
    }

    public void insert(
            Connection mart, long loadBatchId, LocalDate baseDate, List<BalanceRow> rows, Pseudonymizer pseudonymizer)
            throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(INSERT_SQL)) {
            int pending = 0;
            for (BalanceRow row : rows) {
                statement.setObject(1, baseDate);
                statement.setString(2, pseudonymizer.pseudonymize(row.accountId()));
                statement.setString(3, pseudonymizer.pseudonymize(row.customerId()));
                statement.setString(4, row.accountType());
                statement.setObject(5, row.productId(), Types.BIGINT);
                statement.setString(6, row.productGroup());
                statement.setLong(7, row.balance());
                statement.setLong(8, loadBatchId);
                statement.addBatch();
                if (++pending == BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    public long countRows(Connection mart, LocalDate baseDate) throws SQLException {
        return queryLong(mart, "SELECT COUNT(*) FROM mart_daily_account_balance WHERE base_date = ?", baseDate);
    }

    public long sumBalance(Connection mart, LocalDate baseDate) throws SQLException {
        return queryLong(
                mart, "SELECT COALESCE(SUM(balance), 0) FROM mart_daily_account_balance WHERE base_date = ?", baseDate);
    }

    private long queryLong(Connection mart, String sql, LocalDate baseDate) throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(sql)) {
            statement.setObject(1, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
