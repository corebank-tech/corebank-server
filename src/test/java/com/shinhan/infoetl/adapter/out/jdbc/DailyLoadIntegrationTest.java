package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import com.shinhan.infoetl.TestKeys;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.service.DailyLoadService;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
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

    private static final LocalDate D = LocalDate.of(2026, 10, 20); // 화
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate WED = LocalDate.of(2026, 10, 21);
    // 2026-10-21 01:00 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T16:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 1, 0, 0);

    private final Pseudonymizer pseudonymizer = new Pseudonymizer(TestKeys.bytes("s"));
    private final MartBalanceWriter writer = new MartBalanceWriter();
    private final LoadBatchRepository repository = new LoadBatchRepository();
    private final DailyLoadService service = new DailyLoadService(
            new JdbcReplicaBalanceAdapter(InfoEtlTestDatabase::replica),
            new JdbcReplicaCalendarAdapter(InfoEtlTestDatabase::replica),
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

        LoadResult second = service.load(D, true);

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

        assertThatThrownBy(() -> service.load(D, true)).isInstanceOf(EtlStorageException.class);

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

        assertThatThrownBy(() -> service.load(D, true)).isInstanceOf(EtlStorageException.class);

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

        assertThatThrownBy(() -> service.load(D, true)).isInstanceOf(IllegalStateException.class);

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

    @Test
    @DisplayName("레플리카가 그대로면 재적재해도 건수와 합계, 행이 같다")
    void reloadWithUnchangedReplicaKeepsCountsAndTotals() throws SQLException {
        LoadResult first = service.load(D);
        java.util.List<String> keysBefore;
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            keysBefore = accountKeys(mart);
        }

        LoadResult second = service.load(D, true);

        assertThat(second.balanceRowCount()).isEqualTo(first.balanceRowCount());
        assertThat(second.martBalanceTotal()).isEqualTo(first.martBalanceTotal());
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(first.balanceRowCount());
            assertThat(writer.sumBalance(mart, D)).isEqualTo(first.martBalanceTotal());
            assertThat(accountKeys(mart)).containsExactlyInAnyOrderElementsOf(keysBefore);
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("REPLACED");
            assertThat(statusOf(mart, second.loadBatchId())).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("첫 적재의 asOf는 기준일이고 적재 기록에도 남는다")
    void firstLoadAsOfIsTheBaseDate() throws SQLException {
        LoadResult result = service.load(D);

        assertThat(result.asOfDate()).isEqualTo(D);
        try (Connection mart = InfoEtlTestDatabase.mart();
                ResultSet row = batch(mart, result.loadBatchId())) {
            assertThat(row.getObject("as_of_date", LocalDate.class)).isEqualTo(D);
        }
    }

    @Test
    @DisplayName("영업일이 이어서 적재되면 asOf도 따라 올라간다")
    void asOfAdvancesWithContiguousLoads() throws SQLException {
        ReplicaFixtures.insertLedger(50L, 1L, 250L, LocalDateTime.of(2026, 10, 19, 9, 0), MON);

        LoadResult monday = service.load(MON);
        LoadResult tuesday = service.load(D);

        assertThat(monday.asOfDate()).isEqualTo(MON);
        assertThat(tuesday.asOfDate()).isEqualTo(D);
    }

    @Test
    @DisplayName("중간 영업일이 비면 asOf는 머물고 그 날을 적재하면 뒤 날짜까지 따라잡는다")
    void asOfStopsAtGapAndCatchesUpOnBackfill() throws SQLException {
        ReplicaFixtures.insertLedger(50L, 1L, 250L, LocalDateTime.of(2026, 10, 19, 9, 0), MON);
        ReplicaFixtures.insertLedger(60L, 1L, 800L, LocalDateTime.of(2026, 10, 21, 9, 0), WED);

        service.load(MON);
        LoadResult wednesday = service.load(WED);
        LoadResult tuesday = service.load(D);

        assertThat(wednesday.asOfDate()).isEqualTo(MON);
        assertThat(tuesday.asOfDate()).isEqualTo(WED);
    }

    @Test
    @DisplayName("첫 적재일이 실패하면 뒤 날짜가 완료돼도 asOf는 null이고, 첫 적재일을 재적재하면 따라잡는다")
    void failedFirstLoadDayHoldsBackTheWatermark() throws SQLException {
        ReplicaFixtures.insertLedger(60L, 1L, 800L, LocalDateTime.of(2026, 10, 20, 9, 0), D);
        JdbcMartLoadAdapter martAdapter = new JdbcMartLoadAdapter(InfoEtlTestDatabase::mart, CLOCK);
        long monday = martAdapter.start(MON, java.time.Duration.ofHours(6));
        martAdapter.fail(monday, "boom");

        LoadResult tuesday = service.load(D);
        assertThat(tuesday.asOfDate()).isNull();

        ReplicaFixtures.insertLedger(50L, 1L, 250L, LocalDateTime.of(2026, 10, 19, 9, 0), MON);
        LoadResult mondayRetry = service.load(MON);

        assertThat(mondayRetry.asOfDate()).isEqualTo(D);
    }

    @Test
    @DisplayName("COMPLETED인 기준일은 rebuild 없이 거부되고 마트는 그대로이며, rebuild를 주면 다시 만든다")
    void completedBaseDateNeedsRebuildFlag() throws SQLException {
        LoadResult first = service.load(D);

        assertThatThrownBy(() -> service.load(D)).isInstanceOf(IllegalArgumentException.class);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("COMPLETED");
            assertThat(repository.firstBaseDate(mart)).isEqualTo(D);
        }
        LoadResult rebuilt = service.load(D, true);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("REPLACED");
            assertThat(statusOf(mart, rebuilt.loadBatchId())).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("재적재가 T2에서 실패하면 이전 행과 COMPLETED 배치, asOf가 그대로 남는다")
    void failedRebuildInsideLoadKeepsPreviousDataAndAsOf() throws SQLException {
        LoadResult first = service.load(D);
        JdbcMartLoadAdapter martAdapter = new JdbcMartLoadAdapter(InfoEtlTestDatabase::mart, CLOCK);
        long rebuild = martAdapter.start(D, java.time.Duration.ofHours(6));
        java.util.List<MartBalanceRow> newRows = java.util.List.of(new MartBalanceRow(
                pseudonymizer.accountKey(9L), pseudonymizer.customerKey(19L), "DEMAND_DEPOSIT", null, null, 5L));

        assertThatThrownBy(() -> martAdapter.publish(rebuild, D, newRows, (firstLoadDate, dates) -> {
                    throw new IllegalStateException("asOf 계산 중 실패");
                }))
                .isInstanceOf(IllegalStateException.class);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_000L);
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("COMPLETED");
            try (ResultSet row = batch(mart, first.loadBatchId())) {
                assertThat(row.getObject("as_of_date", LocalDate.class)).isEqualTo(D);
            }
        }
    }

    @Test
    @DisplayName("빈 날이 공휴일이면 asOf는 멈추지 않는다")
    void asOfSkipsHolidayInsteadOfStopping() throws SQLException {
        ReplicaFixtures.insertHoliday(D, "임시공휴일");
        ReplicaFixtures.insertLedger(50L, 1L, 250L, LocalDateTime.of(2026, 10, 19, 9, 0), MON);
        ReplicaFixtures.insertLedger(60L, 1L, 800L, LocalDateTime.of(2026, 10, 21, 9, 0), WED);

        service.load(MON);
        LoadResult wednesday = service.load(WED);

        assertThat(wednesday.asOfDate()).isEqualTo(WED);
    }

    @Test
    @DisplayName("주말을 기준일로 주면 거부하고 마트에 적재 기록도 남기지 않는다")
    void weekendBaseDateLeavesNoTrace() throws SQLException {
        assertThatThrownBy(() -> service.load(LocalDate.of(2026, 10, 24))).isInstanceOf(IllegalArgumentException.class);

        try (Connection mart = InfoEtlTestDatabase.mart();
                Statement statement = mart.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM mart_load_batch")) {
            resultSet.next();
            assertThat(resultSet.getLong(1)).isZero();
        }
    }

    @Test
    @DisplayName("정리되어 FAILED가 된 배치가 뒤늦게 끝나도 완료로 덮이지 않고 쓴 행도 롤백된다")
    void lateFinishOfCleanedUpBatchIsRolledBack() throws SQLException {
        LoadResult first = service.load(D);
        JdbcMartLoadAdapter martAdapter = new JdbcMartLoadAdapter(InfoEtlTestDatabase::mart, CLOCK);
        long slow = martAdapter.start(D, java.time.Duration.ofHours(6));
        try (Connection mart = InfoEtlTestDatabase.mart();
                PreparedStatement statement =
                        mart.prepareStatement("UPDATE mart_load_batch SET started_at = ? WHERE load_batch_id = ?")) {
            statement.setObject(1, NOW.minusHours(7));
            statement.setLong(2, slow);
            statement.executeUpdate();
        }
        martAdapter.start(D.plusDays(1), java.time.Duration.ofHours(6)); // 다른 프로세스의 시작이 slow를 정리한다
        java.util.List<MartBalanceRow> lateRows = java.util.List.of(new MartBalanceRow(
                pseudonymizer.accountKey(9L), pseudonymizer.customerKey(19L), "DEMAND_DEPOSIT", null, null, 5L));

        assertThatThrownBy(() -> martAdapter.publish(slow, D, lateRows, (firstLoadDate, dates) -> D))
                .isInstanceOf(IllegalStateException.class);

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(statusOf(mart, slow)).isEqualTo("FAILED");
            assertThat(statusOf(mart, first.loadBatchId())).isEqualTo("COMPLETED");
            assertThat(writer.countRows(mart, D)).isEqualTo(2L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(1_000L);
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
