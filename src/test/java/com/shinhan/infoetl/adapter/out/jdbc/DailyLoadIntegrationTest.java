package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.service.DailyLoadService;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.Pseudonymizer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyLoadIntegrationTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);
    // 2026-10-21 01:00 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T16:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 1, 0, 0);

    private final Pseudonymizer pseudonymizer =
            new Pseudonymizer("service-test-key-0001-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private final MartBalanceWriter writer = new MartBalanceWriter();
    private final LoadBatchRepository repository = new LoadBatchRepository();
    private final DailyLoadService service = new DailyLoadService(
            new JdbcReplicaBalanceAdapter(InfoEtlTestDatabase::replica),
            new JdbcMartLoadAdapter(InfoEtlTestDatabase::mart, CLOCK),
            pseudonymizer);

    @BeforeEach
    void seedReplicaAndResetMart() throws SQLException {
        InfoEtlTestDatabase.resetReplica();
        InfoEtlTestDatabase.resetMart();
        ReplicaFixtures.insertProduct(100L, "DEPOSIT");
        ReplicaFixtures.insertAccount(1L, 11L, null, "DEMAND_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertAccount(2L, 12L, 100L, "TIME_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertLedger(1L, 1L, 300L, LocalDateTime.of(2026, 10, 20, 9, 0), D);
        ReplicaFixtures.insertLedger(2L, 2L, 700L, LocalDateTime.of(2026, 10, 20, 9, 5), D);
    }

    @Test
    @DisplayName("첫 적재는 마트 행을 채우고 COMPLETED로 기록한다")
    void firstLoadFillsMartAndCompletes() throws SQLException {
        LoadResult result = service.load(D);

        assertThat(result.balanceRowCount()).isEqualTo(2L);
        assertThat(result.martBalanceTotal()).isEqualTo(1_000L);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_000L);
            assertThat(accountKeys(mart))
                    .containsExactlyInAnyOrder(pseudonymizer.accountKey(1L), pseudonymizer.accountKey(2L));
            try (ResultSet row = batch(mart, result.loadBatchId())) {
                assertThat(row.getString("status")).isEqualTo("COMPLETED");
                assertThat(row.getObject("started_at", LocalDateTime.class)).isEqualTo(NOW);
                assertThat(row.getObject("completed_at", LocalDateTime.class)).isEqualTo(NOW);
                assertThat(row.getLong("balance_row_count")).isEqualTo(2L);
                assertThat(row.getLong("mart_balance_total")).isEqualTo(1_000L);
            }
        }
    }

    @Test
    @DisplayName("재적재하면 새 값으로 교체되고 이전 배치는 REPLACED가 된다")
    void reloadReplacesRowsAndPreviousBatch() throws SQLException {
        LoadResult first = service.load(D);
        ReplicaFixtures.insertLedger(3L, 1L, 450L, LocalDateTime.of(2026, 10, 20, 18, 0), D);

        LoadResult second = service.load(D);

        assertThat(second.loadBatchId()).isNotEqualTo(first.loadBatchId());
        assertThat(second.balanceRowCount()).isEqualTo(2L);
        assertThat(second.martBalanceTotal()).isEqualTo(1_150L);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_150L);
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("REPLACED");
            assertThat(statusOf(mart, second.loadBatchId())).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("레플리카 읽기가 실패하면 마트의 이전 데이터와 COMPLETED는 그대로고 새 배치만 FAILED다")
    void replicaReadFailureKeepsPreviousLoad() throws SQLException {
        LoadResult first = service.load(D);
        dropReplicaLedger();

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(EtlStorageException.class);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_000L);
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("COMPLETED");
            try (ResultSet row = batch(mart, latestBatchId(mart))) {
                assertThat(row.getString("status")).isEqualTo("FAILED");
                assertThat(row.getString("failure_reason")).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("마트 삽입 도중 실패하면 삭제까지 롤백되어 이전 데이터가 그대로 남는다")
    void insertFailureRollsBackDeletion() throws SQLException {
        LoadResult first = service.load(D);
        try (Connection replica = InfoEtlTestDatabase.replica();
                Statement statement = replica.createStatement()) {
            statement.execute("ALTER TABLE account MODIFY account_type VARCHAR(24) NULL");
            statement.execute("UPDATE account SET account_type = NULL WHERE account_id = 2");
        }

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(EtlStorageException.class);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_000L);
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("COMPLETED");
            try (ResultSet row = batch(mart, latestBatchId(mart))) {
                assertThat(row.getString("status")).isEqualTo("FAILED");
            }
        }
    }

    @Test
    @DisplayName("6시간 넘게 RUNNING으로 남은 기록은 다음 적재를 시작할 때 FAILED로 정리한다")
    void cleansUpStaleRunningAtStart() throws SQLException {
        long stale;
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            stale = repository.start(mart, D.minusDays(3), NOW.minusHours(7));
        }

        service.load(D);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(statusOf(mart, stale)).isEqualTo("FAILED");
        }
    }

    @Test
    @DisplayName("레플리카에서 읽은 잔액이 0건이면 실패 처리하고 마트의 이전 데이터는 그대로다")
    void emptyReplicaResultKeepsPreviousLoad() throws SQLException {
        LoadResult first = service.load(D);
        try (Connection replica = InfoEtlTestDatabase.replica();
                Statement statement = replica.createStatement()) {
            statement.execute("DELETE FROM ledger_entry");
        }

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalStateException.class);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_000L);
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("COMPLETED");
            try (ResultSet row = batch(mart, latestBatchId(mart))) {
                assertThat(row.getString("status")).isEqualTo("FAILED");
                assertThat(row.getString("failure_reason")).contains("0건");
            }
        }
    }

    private static void dropReplicaLedger() throws SQLException {
        try (Connection replica = InfoEtlTestDatabase.replica();
                Statement statement = replica.createStatement()) {
            statement.execute("DROP TABLE ledger_entry");
        }
    }

    private static java.util.List<String> accountKeys(Connection mart) throws SQLException {
        java.util.List<String> keys = new java.util.ArrayList<>();
        try (Statement statement = mart.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT account_key FROM mart_daily_account_balance")) {
            while (resultSet.next()) {
                keys.add(resultSet.getString(1));
            }
        }
        return keys;
    }

    private static long latestBatchId(Connection mart) throws SQLException {
        try (Statement statement = mart.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT MAX(load_batch_id) FROM mart_load_batch")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static ResultSet batch(Connection mart, long id) throws SQLException {
        PreparedStatement statement = mart.prepareStatement("SELECT * FROM mart_load_batch WHERE load_batch_id = ?");
        statement.setLong(1, id);
        ResultSet resultSet = statement.executeQuery();
        assertThat(resultSet.next()).isTrue();
        return resultSet;
    }

    private static String statusOf(Connection mart, long id) throws SQLException {
        try (ResultSet row = batch(mart, id)) {
            return row.getString("status");
        }
    }
}
