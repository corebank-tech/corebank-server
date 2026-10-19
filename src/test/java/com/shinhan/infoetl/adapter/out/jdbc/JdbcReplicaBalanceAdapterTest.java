package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import com.shinhan.infoetl.domain.BalanceRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcReplicaBalanceAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final JdbcReplicaBalanceAdapter adapter = new JdbcReplicaBalanceAdapter(InfoEtlTestDatabase::replica);

    @BeforeEach
    void resetReplica() throws SQLException {
        InfoEtlTestDatabase.resetReplica();
        ReplicaFixtures.insertProduct(100L, "DEPOSIT");
    }

    @Test
    @DisplayName("기준일까지의 마지막 기표 잔액을 고른다")
    void picksLastBalanceUpToBaseDate() throws SQLException {
        insertAccount(1L, 11L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(1L, 1L, 100L, LocalDateTime.of(2026, 10, 19, 10, 0), D.minusDays(1));
        insertLedger(2L, 1L, 300L, LocalDateTime.of(2026, 10, 20, 9, 0), D);

        assertThat(read().get(1L).balance()).isEqualTo(300L);
    }

    @Test
    @DisplayName("발생 시각이 같으면 원장 ID가 큰 기표의 잔액을 고른다")
    void breaksTieByLedgerEntryId() throws SQLException {
        insertAccount(2L, 12L, null, "DEMAND_DEPOSIT", "ACTIVE");
        LocalDateTime sameTime = LocalDateTime.of(2026, 10, 20, 10, 0);
        insertLedger(10L, 2L, 500L, sameTime, D);
        insertLedger(11L, 2L, 700L, sameTime, D);

        assertThat(read().get(2L).balance()).isEqualTo(700L);
    }

    @Test
    @DisplayName("거래일이 NULL이면 발생일을 거래일로 본다")
    void usesOccurredDateWhenTradeDateIsNull() throws SQLException {
        insertAccount(3L, 13L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(20L, 3L, 400L, LocalDateTime.of(2026, 10, 20, 23, 0), null);
        insertAccount(4L, 14L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(21L, 4L, 900L, LocalDateTime.of(2026, 10, 21, 1, 0), null);

        Map<Long, BalanceRow> rows = read();

        assertThat(rows.get(3L).balance()).isEqualTo(400L);
        assertThat(rows).doesNotContainKey(4L);
    }

    @Test
    @DisplayName("발생일이 기준일 다음 날이어도 거래일이 기준일이면 포함한다")
    void includesEntryByTradeDateEvenIfOccurredAfterBaseDate() throws SQLException {
        insertAccount(5L, 15L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(30L, 5L, 1_000L, LocalDateTime.of(2026, 10, 21, 0, 30), D);

        assertThat(read().get(5L).balance()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("거래일이 기준일 다음 날인 기표는 잔액에 반영하지 않는다")
    void ignoresEntriesAfterBaseDate() throws SQLException {
        insertAccount(6L, 16L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(40L, 6L, 50L, LocalDateTime.of(2026, 10, 19, 10, 0), D.minusDays(1));
        insertLedger(41L, 6L, 80L, LocalDateTime.of(2026, 10, 21, 10, 0), D.plusDays(1));

        assertThat(read().get(6L).balance()).isEqualTo(50L);
    }

    @Test
    @DisplayName("발생 시각이 기준일 7일 뒤 이후인 기표는 읽지 않는다")
    void ignoresEntriesBeyondSevenDayWindow() throws SQLException {
        insertAccount(7L, 17L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(50L, 7L, 10L, LocalDateTime.of(2026, 10, 19, 10, 0), D.minusDays(1));
        insertLedger(51L, 7L, 99L, LocalDateTime.of(2026, 10, 27, 0, 0), D);
        insertAccount(8L, 18L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(52L, 8L, 77L, LocalDateTime.of(2026, 10, 26, 23, 0), D);

        Map<Long, BalanceRow> rows = read();

        assertThat(rows.get(7L).balance()).isEqualTo(10L);
        assertThat(rows.get(8L).balance()).isEqualTo(77L);
    }

    @Test
    @DisplayName("해지일이 기준일이면 0원 행을 포함한다")
    void includesClosedAccountOnClosingDay() throws SQLException {
        insertAccount(9L, 19L, 100L, "TIME_DEPOSIT", "CLOSED");
        insertLedger(60L, 9L, 1_000L, LocalDateTime.of(2026, 10, 19, 10, 0), D.minusDays(1));
        insertLedger(61L, 9L, 0L, LocalDateTime.of(2026, 10, 20, 15, 0), D);

        assertThat(read().get(9L).balance()).isZero();
    }

    @Test
    @DisplayName("해지일이 기준일보다 앞이면 행을 만들지 않는다")
    void excludesAccountClosedBeforeBaseDate() throws SQLException {
        insertAccount(10L, 20L, 100L, "TIME_DEPOSIT", "CLOSED");
        insertLedger(70L, 10L, 1_000L, LocalDateTime.of(2026, 10, 17, 10, 0), D.minusDays(3));
        insertLedger(71L, 10L, 0L, LocalDateTime.of(2026, 10, 19, 10, 0), D.minusDays(1));

        assertThat(read()).doesNotContainKey(10L);
    }

    @Test
    @DisplayName("해지일이 기준일보다 뒤면 기준일 시점의 잔액으로 포함한다")
    void includesAccountClosedAfterBaseDateWithBalanceAtBaseDate() throws SQLException {
        insertAccount(11L, 21L, 100L, "TIME_DEPOSIT", "CLOSED");
        insertLedger(80L, 11L, 1_000L, LocalDateTime.of(2026, 10, 19, 10, 0), D.minusDays(1));
        insertLedger(81L, 11L, 0L, LocalDateTime.of(2026, 10, 22, 10, 0), D.plusDays(2));

        assertThat(read().get(11L).balance()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("입출금계좌는 상품 정보가 비고 상품계좌는 상품 그룹을 채운다")
    void mapsProductColumns() throws SQLException {
        insertAccount(12L, 22L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(90L, 12L, 1L, LocalDateTime.of(2026, 10, 20, 9, 0), D);
        insertAccount(13L, 23L, 100L, "TIME_DEPOSIT", "ACTIVE");
        insertLedger(91L, 13L, 2L, LocalDateTime.of(2026, 10, 20, 9, 0), D);

        Map<Long, BalanceRow> rows = read();

        assertThat(rows.get(12L).productId()).isNull();
        assertThat(rows.get(12L).productGroup()).isNull();
        assertThat(rows.get(13L).productId()).isEqualTo(100L);
        assertThat(rows.get(13L).productGroup()).isEqualTo("DEPOSIT");
        assertThat(rows.get(13L).customerId()).isEqualTo(23L);
        assertThat(rows.get(13L).accountType()).isEqualTo("TIME_DEPOSIT");
    }

    @Test
    @DisplayName("해지 기표가 7일 창 밖이어도 기준일에 열려 있던 계좌는 그날 잔액으로 포함한다")
    void includesAccountClosedBeyondSevenDayWindow() throws SQLException {
        insertAccount(15L, 25L, null, "DEMAND_DEPOSIT", "CLOSED");
        insertLedger(100L, 15L, 1_000L, LocalDateTime.of(2026, 10, 10, 10, 0), D.minusDays(10));
        insertLedger(101L, 15L, 0L, LocalDateTime.of(2026, 11, 19, 10, 0), D.plusDays(30));

        assertThat(read().get(15L).balance()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("해지 기표가 7일 창 밖이고 해지일이 기준일 이전이면 포함하지 않는다")
    void excludesAccountClosedBeforeBaseDateEvenWhenOtherEntriesAreOutsideWindow() throws SQLException {
        insertAccount(16L, 26L, null, "DEMAND_DEPOSIT", "CLOSED");
        insertLedger(110L, 16L, 500L, LocalDateTime.of(2026, 10, 1, 10, 0), D.minusDays(19));
        insertLedger(111L, 16L, 0L, LocalDateTime.of(2026, 10, 5, 10, 0), D.minusDays(15));

        assertThat(read()).doesNotContainKey(16L);
    }

    @Test
    @DisplayName("토요일에 발생한 거래일 NULL 기표는 다음 월요일 거래로 본다")
    void rollsNullTradeDateOnWeekendToNextBusinessDay() throws SQLException {
        insertAccount(30L, 40L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(200L, 30L, 100L, LocalDateTime.of(2026, 10, 17, 10, 0), null);

        assertThat(readOn(LocalDate.of(2026, 10, 18))).doesNotContainKey(30L);
        assertThat(readOn(LocalDate.of(2026, 10, 19)).get(30L).balance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("평일 공휴일에 발생한 거래일 NULL 기표는 다음 영업일 거래로 본다")
    void rollsNullTradeDateOnWeekdayHolidayToNextBusinessDay() throws SQLException {
        ReplicaFixtures.insertHoliday(LocalDate.of(2026, 10, 21), "임시공휴일");
        insertAccount(31L, 41L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(201L, 31L, 100L, LocalDateTime.of(2026, 10, 21, 10, 0), null);

        assertThat(readOn(LocalDate.of(2026, 10, 21))).doesNotContainKey(31L);
        assertThat(readOn(LocalDate.of(2026, 10, 22)).get(31L).balance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("공휴일과 주말이 이어지면 연휴가 끝난 첫 영업일로 올린다")
    void rollsAcrossHolidayAndWeekendChain() throws SQLException {
        ReplicaFixtures.insertHoliday(LocalDate.of(2026, 10, 23), "임시공휴일");
        insertAccount(32L, 42L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(202L, 32L, 100L, LocalDateTime.of(2026, 10, 23, 10, 0), null);

        assertThat(readOn(LocalDate.of(2026, 10, 25))).doesNotContainKey(32L);
        assertThat(readOn(LocalDate.of(2026, 10, 26)).get(32L).balance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("영업일에 발생한 거래일 NULL 기표는 올리지 않고 그날 거래로 본다")
    void keepsNullTradeDateOnBusinessDay() throws SQLException {
        insertAccount(33L, 43L, null, "DEMAND_DEPOSIT", "ACTIVE");
        insertLedger(203L, 33L, 100L, LocalDateTime.of(2026, 10, 20, 10, 0), null);

        assertThat(readOn(LocalDate.of(2026, 10, 19))).doesNotContainKey(33L);
        assertThat(readOn(D).get(33L).balance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("휴일에 기표된 해지 0원 행은 다음 영업일에 해지일을 갖고 그날 0원 행으로 나온다")
    void closingEntryOnWeekendIsClosedOnNextBusinessDay() throws SQLException {
        insertAccount(34L, 44L, 100L, "TIME_DEPOSIT", "CLOSED");
        insertLedger(204L, 34L, 1_000L, LocalDateTime.of(2026, 10, 15, 10, 0), LocalDate.of(2026, 10, 15));
        insertLedger(205L, 34L, 0L, LocalDateTime.of(2026, 10, 17, 10, 0), null);

        assertThat(readOn(LocalDate.of(2026, 10, 16)).get(34L).balance()).isEqualTo(1_000L);
        assertThat(readOn(LocalDate.of(2026, 10, 19)).get(34L).balance()).isZero();
        assertThat(readOn(D)).doesNotContainKey(34L);
    }

    @Test
    @DisplayName("원장 기표가 없는 계좌는 행을 만들지 않는다")
    void excludesAccountWithoutLedgerEntries() throws SQLException {
        insertAccount(14L, 24L, null, "DEMAND_DEPOSIT", "ACTIVE");

        assertThat(read()).isEmpty();
    }

    private Map<Long, BalanceRow> read() {
        return readOn(D);
    }

    private Map<Long, BalanceRow> readOn(LocalDate baseDate) {
        List<BalanceRow> rows = adapter.readBalances(baseDate);
        return rows.stream().collect(Collectors.toMap(BalanceRow::accountId, Function.identity()));
    }

    private void insertAccount(long accountId, long customerId, Long productId, String type, String status)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO account (account_id, customer_id, product_id, account_type, status)"
                                + " VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setLong(2, customerId);
            statement.setObject(3, productId);
            statement.setString(4, type);
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private void insertLedger(
            long ledgerEntryId, long accountId, long balanceAfter, LocalDateTime occurredAt, LocalDate tradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ledger_entry (ledger_entry_id, account_id, balance_after, occurred_at,"
                                + " trade_date) VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, ledgerEntryId);
            statement.setLong(2, accountId);
            statement.setLong(3, balanceAfter);
            statement.setObject(4, occurredAt);
            statement.setObject(5, tradeDate);
            statement.executeUpdate();
        }
    }
}
