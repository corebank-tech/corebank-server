package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoadBatchRepositoryTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 10, 21, 1, 0, 0);
    private static final LocalDateTime COMPLETED = LocalDateTime.of(2026, 10, 21, 1, 5, 30);

    private final LoadBatchRepository repository = new LoadBatchRepository();

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("시작하면 RUNNING 행이 생기고 ID를 돌려준다")
    void startCreatesRunningRow() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long id = repository.start(mart, D, STARTED);

            try (ResultSet row = select(mart, id)) {
                assertThat(row.getString("status")).isEqualTo("RUNNING");
                assertThat(row.getObject("base_date", LocalDate.class)).isEqualTo(D);
                assertThat(row.getObject("started_at", LocalDateTime.class)).isEqualTo(STARTED);
                assertThat(row.getObject("completed_at")).isNull();
            }
        }
    }

    @Test
    @DisplayName("완료하면 상태와 완료 시각, 건수, 합계를 기록한다")
    void markCompletedRecordsResult() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long id = repository.start(mart, D, STARTED);

            repository.markCompleted(mart, id, COMPLETED, 150_000L, 1_523_400_000_000L, D);

            try (ResultSet row = select(mart, id)) {
                assertThat(row.getString("status")).isEqualTo("COMPLETED");
                assertThat(row.getObject("completed_at", LocalDateTime.class)).isEqualTo(COMPLETED);
                assertThat(row.getLong("balance_row_count")).isEqualTo(150_000L);
                assertThat(row.getLong("mart_balance_total")).isEqualTo(1_523_400_000_000L);
            }
        }
    }

    @Test
    @DisplayName("완료하면 asOf 날짜도 기록한다")
    void markCompletedRecordsAsOfDate() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long id = repository.start(mart, D, STARTED);

            repository.markCompleted(mart, id, COMPLETED, 10L, 100L, D.minusDays(3));

            try (ResultSet row = select(mart, id)) {
                assertThat(row.getObject("as_of_date", LocalDate.class)).isEqualTo(D.minusDays(3));
            }
        }
    }

    @Test
    @DisplayName("RUNNING이 아닌 배치는 완료 처리하지 않고 상태를 그대로 둔다")
    void markCompletedRejectsBatchThatIsNotRunning() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long staleFailed = repository.start(mart, D, STARTED.minusHours(7));
            repository.failStaleRunning(mart, STARTED, "비정상 종료로 간주함");

            assertThatThrownBy(() -> repository.markCompleted(mart, staleFailed, COMPLETED, 10L, 100L, D))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RUNNING");

            try (ResultSet row = select(mart, staleFailed)) {
                assertThat(row.getString("status")).isEqualTo("FAILED");
                assertThat(row.getObject("completed_at")).isNull();
                assertThat(row.getObject("as_of_date")).isNull();
            }
        }
    }

    @Test
    @DisplayName("완료된 기준일만 중복 없이 돌려준다")
    void completedBaseDatesOnlyContainsCompleted() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long first = repository.start(mart, D, STARTED);
            repository.markCompleted(mart, first, COMPLETED, 10L, 100L, D);
            long replaced = repository.start(mart, D, STARTED);
            repository.markCompleted(mart, replaced, COMPLETED, 10L, 100L, D);
            long failed = repository.start(mart, D.plusDays(1), STARTED);
            repository.markFailed(mart, failed, "boom");
            repository.start(mart, D.plusDays(2), STARTED);

            assertThat(repository.completedBaseDates(mart)).containsExactly(D);
        }
    }

    @Test
    @DisplayName("첫 기준일은 상태와 무관하게 가장 이른 적재 기록의 날짜다")
    void firstBaseDateIgnoresStatus() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long failed = repository.start(mart, D, STARTED);
            repository.markFailed(mart, failed, "boom");
            long completed = repository.start(mart, D.plusDays(1), STARTED);
            repository.markCompleted(mart, completed, COMPLETED, 10L, 100L, D.plusDays(1));

            assertThat(repository.firstBaseDate(mart)).isEqualTo(D);
        }
    }

    @Test
    @DisplayName("asOf가 null이어도 완료 처리하고 as_of_date를 NULL로 남긴다")
    void markCompletedAllowsNullAsOf() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long id = repository.start(mart, D, STARTED);

            repository.markCompleted(mart, id, COMPLETED, 10L, 100L, null);

            try (ResultSet row = select(mart, id)) {
                assertThat(row.getString("status")).isEqualTo("COMPLETED");
                assertThat(row.getObject("as_of_date", LocalDate.class)).isNull();
            }
        }
    }

    @Test
    @DisplayName("실패하면 FAILED와 사유를 기록하고 200자를 넘는 사유는 자른다")
    void markFailedTruncatesLongReason() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long id = repository.start(mart, D, STARTED);

            repository.markFailed(mart, id, "x".repeat(250));

            try (ResultSet row = select(mart, id)) {
                assertThat(row.getString("status")).isEqualTo("FAILED");
                assertThat(row.getString("failure_reason")).hasSize(200);
            }
        }
    }

    @Test
    @DisplayName("재적재하면 같은 기준일의 이전 COMPLETED만 REPLACED로 바뀐다")
    void replacePreviousOnlyAffectsSameDateCompleted() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long previous = repository.start(mart, D, STARTED);
            repository.markCompleted(mart, previous, COMPLETED, 10L, 100L, D);
            long failed = repository.start(mart, D, STARTED);
            repository.markFailed(mart, failed, "boom");
            long otherDate = repository.start(mart, D.plusDays(1), STARTED);
            repository.markCompleted(mart, otherDate, COMPLETED, 10L, 100L, D);
            long current = repository.start(mart, D, STARTED);
            repository.markCompleted(mart, current, COMPLETED, 12L, 120L, D);

            int replaced = repository.replacePrevious(mart, D, current);

            assertThat(replaced).isEqualTo(1);
            assertThat(statusOf(mart, previous)).isEqualTo("REPLACED");
            assertThat(statusOf(mart, failed)).isEqualTo("FAILED");
            assertThat(statusOf(mart, otherDate)).isEqualTo("COMPLETED");
            assertThat(statusOf(mart, current)).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("이전 적재가 REPLACED가 되어도 건수와 합계 기록은 남는다")
    void replacedRowKeepsItsRecordedTotals() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long previous = repository.start(mart, D, STARTED);
            repository.markCompleted(mart, previous, COMPLETED, 10L, 100L, D);
            long current = repository.start(mart, D, STARTED);

            repository.replacePrevious(mart, D, current);

            try (ResultSet row = select(mart, previous)) {
                assertThat(row.getString("status")).isEqualTo("REPLACED");
                assertThat(row.getLong("balance_row_count")).isEqualTo(10L);
                assertThat(row.getLong("mart_balance_total")).isEqualTo(100L);
                assertThat(row.getObject("completed_at", LocalDateTime.class)).isEqualTo(COMPLETED);
            }
        }
    }

    @Test
    @DisplayName("기준 시각보다 오래된 RUNNING만 FAILED로 바꾸고 사유를 남긴다")
    void failStaleRunningOnlyAffectsOldRunning() throws SQLException {
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0, 0);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            long stale = repository.start(mart, D, now.minusHours(7));
            long fresh = repository.start(mart, D.plusDays(1), now.minusHours(1));
            long completed = repository.start(mart, D.minusDays(1), now.minusHours(8));
            repository.markCompleted(mart, completed, now.minusHours(7), 5L, 50L, D);
            long failed = repository.start(mart, D.minusDays(2), now.minusHours(9));
            repository.markFailed(mart, failed, "boom");

            int changed = repository.failStaleRunning(mart, now.minusHours(6), "비정상 종료로 간주함");

            assertThat(changed).isEqualTo(1);
            assertThat(statusOf(mart, stale)).isEqualTo("FAILED");
            assertThat(statusOf(mart, fresh)).isEqualTo("RUNNING");
            assertThat(statusOf(mart, completed)).isEqualTo("COMPLETED");
            assertThat(statusOf(mart, failed)).isEqualTo("FAILED");
            try (ResultSet row = select(mart, stale)) {
                assertThat(row.getString("failure_reason")).isEqualTo("비정상 종료로 간주함");
            }
            try (ResultSet row = select(mart, failed)) {
                assertThat(row.getString("failure_reason")).isEqualTo("boom");
            }
        }
    }

    private static ResultSet select(Connection mart, long id) throws SQLException {
        PreparedStatement statement = mart.prepareStatement("SELECT * FROM mart_load_batch WHERE load_batch_id = ?");
        statement.setLong(1, id);
        ResultSet resultSet = statement.executeQuery();
        assertThat(resultSet.next()).isTrue();
        return resultSet;
    }

    private static String statusOf(Connection mart, long id) throws SQLException {
        try (ResultSet row = select(mart, id)) {
            return row.getString("status");
        }
    }
}
