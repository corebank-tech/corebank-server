package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public final class JdbcReplicaCalendarAdapter implements ReplicaCalendarPort {

    private final JdbcConnectionFactory connections;

    public JdbcReplicaCalendarAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public Set<LocalDate> readHolidays(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = new HashSet<>();
        try (Connection replica = connections.open();
                PreparedStatement statement = replica.prepareStatement(
                        "SELECT holiday_date FROM holiday WHERE holiday_date BETWEEN ? AND ?")) {
            replica.setReadOnly(true);
            statement.setObject(1, from);
            statement.setObject(2, to);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    holidays.add(resultSet.getObject(1, LocalDate.class));
                }
            }
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 휴일 읽기에 실패했습니다: " + e.getMessage(), e);
        }
        return holidays;
    }
}
