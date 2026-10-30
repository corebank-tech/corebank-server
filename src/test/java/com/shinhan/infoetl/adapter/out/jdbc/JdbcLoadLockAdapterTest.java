package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.application.port.out.LoadLock;
import com.shinhan.infoetl.domain.LoadAlreadyRunningException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcLoadLockAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final JdbcLoadLockAdapter adapter = new JdbcLoadLockAdapter(InfoEtlTestDatabase::mart);

    @Test
    @DisplayName("같은 기준일을 이미 잡고 있으면 두 번째는 거부한다")
    void rejectsSecondAcquireForSameDate() {
        try (LoadLock ignored = adapter.acquire(D)) {
            assertThatThrownBy(() -> adapter.acquire(D)).isInstanceOf(LoadAlreadyRunningException.class);
        }
    }

    @Test
    @DisplayName("다른 기준일은 동시에 잡을 수 있다")
    void allowsDifferentDatesAtTheSameTime() {
        try (LoadLock first = adapter.acquire(D);
                LoadLock second = adapter.acquire(D.plusDays(1))) {
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
        }
    }

    @Test
    @DisplayName("락을 놓으면 같은 기준일을 다시 잡을 수 있다")
    void canReacquireAfterClose() {
        adapter.acquire(D).close();

        assertThatCode(() -> adapter.acquire(D).close()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("거부된 시도가 연결을 남기지 않는다")
    void rejectedAttemptDoesNotLeakConnections() {
        List<Connection> opened = new ArrayList<>();
        JdbcLoadLockAdapter counting = new JdbcLoadLockAdapter(() -> {
            Connection connection = InfoEtlTestDatabase.mart();
            opened.add(connection);
            return connection;
        });

        try (LoadLock ignored = counting.acquire(D)) {
            assertThatThrownBy(() -> counting.acquire(D)).isInstanceOf(LoadAlreadyRunningException.class);
        }

        assertThat(opened).hasSize(2);
        assertThat(opened)
                .allSatisfy(connection -> assertThat(isClosed(connection)).isTrue());
    }

    @Test
    @DisplayName("프로세스가 죽어 연결이 끊기면 락이 자동으로 풀린다")
    void releasesWhenConnectionDropsWithoutClose() throws SQLException {
        List<Connection> opened = new ArrayList<>();
        JdbcLoadLockAdapter crashing = new JdbcLoadLockAdapter(() -> {
            Connection connection = InfoEtlTestDatabase.mart();
            opened.add(connection);
            return connection;
        });
        crashing.acquire(D); // close()를 부르지 않는다.

        opened.get(0).close(); // 연결이 끊기는 것은 프로세스 종료와 같은 효과다.

        assertThatCode(() -> adapter.acquire(D).close()).doesNotThrowAnyException();
    }

    private static boolean isClosed(Connection connection) {
        try {
            return connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }
}
