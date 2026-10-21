package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.domain.MartEventRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

final class MartEventWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL =
            "INSERT INTO mart_product_subscription_event (base_date, event_type, account_key, customer_key,"
                    + " product_id, product_code, product_name, product_group, load_batch_id)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public int deleteByBaseDate(Connection mart, LocalDate baseDate) throws SQLException {
        try (PreparedStatement statement =
                mart.prepareStatement("DELETE FROM mart_product_subscription_event WHERE base_date = ?")) {
            statement.setObject(1, baseDate);
            return statement.executeUpdate();
        }
    }

    public void insert(Connection mart, long loadBatchId, LocalDate baseDate, List<MartEventRow> rows)
            throws SQLException {
        try (PreparedStatement statement = mart.prepareStatement(INSERT_SQL)) {
            int pending = 0;
            for (MartEventRow row : rows) {
                statement.setObject(1, baseDate);
                statement.setString(2, row.eventType().name());
                statement.setString(3, row.accountKey());
                statement.setString(4, row.customerKey());
                statement.setLong(5, row.productId());
                statement.setString(6, row.productCode());
                statement.setString(7, row.productName());
                statement.setString(8, row.productGroup());
                statement.setLong(9, loadBatchId);
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
        try (PreparedStatement statement =
                mart.prepareStatement("SELECT COUNT(*) FROM mart_product_subscription_event WHERE base_date = ?")) {
            statement.setObject(1, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
