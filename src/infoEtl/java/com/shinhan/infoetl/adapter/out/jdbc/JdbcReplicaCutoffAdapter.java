package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.ReplicaCutoffPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

public final class JdbcReplicaCutoffAdapter implements ReplicaCutoffPort {

    private final JdbcConnectionFactory connections;

    public JdbcReplicaCutoffAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public boolean isClosed(LocalDate baseDate) {
        try (Connection replica = connections.open()) {
            replica.setReadOnly(true);
            try (PreparedStatement statement = replica.prepareStatement(
                    "SELECT 1 FROM business_date_cutoff WHERE base_date = ? AND status = 'CLOSED' LIMIT 1")) {
                statement.setObject(1, baseDate);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 cutoff 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<LocalDate> latestClosedDate() {
        try (Connection replica = connections.open()) {
            replica.setReadOnly(true);
            try (PreparedStatement statement = replica.prepareStatement(
                            "SELECT MAX(base_date) FROM business_date_cutoff WHERE status = 'CLOSED'");
                    ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return Optional.ofNullable(resultSet.getObject(1, LocalDate.class));
            }
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 cutoff 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
