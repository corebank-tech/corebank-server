package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;

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

            repository.markCompleted(mart, id, COMPLETED, 150_000L, 1_523_400_000_000L);

            try (ResultSet row = select(mart, id)) {
                assertThat(row.getString("status")).isEqualTo("COMPLETED");
                assertThat(row.getObject("completed_at", LocalDateTime.class)).isEqualTo(COMPLETED);
                assertThat(row.getLong("balance_row_count")).isEqualTo(150_000L);
                assertThat(row.getLong("mart_balance_total")).isEqualTo(1_523_400_000_000L);
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
            repository.markCompleted(mart, previous, COMPLETED, 10L, 100L);
            long failed = repository.start(mart, D, STARTED);
            repository.markFailed(mart, failed, "boom");
            long otherDate = repository.start(mart, D.plusDays(1), STARTED);
            repository.markCompleted(mart, otherDate, COMPLETED, 10L, 100L);
            long current = repository.start(mart, D, STARTED);
            repository.markCompleted(mart, current, COMPLETED, 12L, 120L);

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
            repository.markCompleted(mart, previous, COMPLETED, 10L, 100L);
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
