package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.LoadLock;
import com.shinhan.infoetl.application.port.out.LoadLockPort;
import com.shinhan.infoetl.domain.LoadAlreadyRunningException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

// MySQL GET_LOCK은 연결에 묶인다. 연결을 쥐고 있는 동안만 락이 유지되고, 프로세스가 죽으면 연결이 끊겨 락이 풀린다.
public final class JdbcLoadLockAdapter implements LoadLockPort {

    private final JdbcConnectionFactory connections;

    public JdbcLoadLockAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public LoadLock acquire(LocalDate baseDate) {
        String lockName = "info_etl:load:" + baseDate;
        Connection connection = null;
        try {
            connection = connections.open();
            if (!tryLock(connection, lockName)) {
                connection.close();
                throw new LoadAlreadyRunningException(baseDate);
            }
            return new HeldLock(connection, lockName);
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new EtlStorageException("적재 락 획득에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private static boolean tryLock(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 락을 못 얻은 경로의 정리이므로 원래 예외를 우선한다.
        }
    }

    private static final class HeldLock implements LoadLock {

        private final Connection connection;
        private final String lockName;

        private HeldLock(Connection connection, String lockName) {
            this.connection = connection;
            this.lockName = lockName;
        }

        @Override
        public void close() {
            try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lockName);
                statement.executeQuery().close();
            } catch (SQLException ignored) {
                // 해제에 실패해도 아래에서 연결을 닫으면 MySQL이 락을 푼다.
            } finally {
                closeQuietly(connection);
            }
        }
    }
}
