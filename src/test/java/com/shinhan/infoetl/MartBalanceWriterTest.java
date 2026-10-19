package com.shinhan.infoetl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MartBalanceWriterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final MartBalanceWriter writer = new MartBalanceWriter();
    private final Pseudonymizer pseudonymizer =
            new Pseudonymizer("writer-test-key-0001".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("계좌·고객 키는 가명 처리되고 원래 ID는 마트에 남지 않는다")
    void writesPseudonymizedKeys() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(
                    mart,
                    1L,
                    D,
                    List.of(new BalanceRow(1234567L, 7654321L, "DEMAND_DEPOSIT", null, null, 500L)),
                    pseudonymizer);

            try (PreparedStatement statement = mart.prepareStatement(
                            "SELECT account_key, customer_key, account_type, product_id, product_group, balance,"
                                    + " load_batch_id FROM mart_daily_account_balance");
                    ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("account_key")).isEqualTo(pseudonymizer.pseudonymize(1234567L));
                assertThat(resultSet.getString("customer_key")).isEqualTo(pseudonymizer.pseudonymize(7654321L));
                assertThat(resultSet.getString("account_key")).matches("^[0-9a-f]{64}$");
                assertThat(resultSet.getString("account_key")).doesNotContain("1234567");
                assertThat(resultSet.getString("account_type")).isEqualTo("DEMAND_DEPOSIT");
                assertThat(resultSet.getObject("product_id")).isNull();
                assertThat(resultSet.getString("product_group")).isNull();
                assertThat(resultSet.getLong("balance")).isEqualTo(500L);
                assertThat(resultSet.getLong("load_batch_id")).isEqualTo(1L);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("지우고 다시 넣어도 건수와 합계가 같다")
    void reloadKeepsCountAndSum() throws SQLException {
        List<BalanceRow> rows = rows(3);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows, pseudonymizer);
            long countBefore = writer.countRows(mart, D);
            long sumBefore = writer.sumBalance(mart, D);

            int deleted = writer.deleteByBaseDate(mart, D);
            writer.insert(mart, 2L, D, rows, pseudonymizer);

            assertThat(deleted).isEqualTo(3);
            assertThat(writer.countRows(mart, D)).isEqualTo(countBefore).isEqualTo(3L);
            assertThat(writer.sumBalance(mart, D)).isEqualTo(sumBefore).isEqualTo(600L);
        }
    }

    @Test
    @DisplayName("삭제는 해당 기준일 행만 지운다")
    void deletesOnlyTargetDate() throws SQLException {
        LocalDate other = D.plusDays(1);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows(2), pseudonymizer);
            writer.insert(mart, 2L, other, rows(4), pseudonymizer);

            writer.deleteByBaseDate(mart, D);

            assertThat(writer.countRows(mart, D)).isZero();
            assertThat(writer.countRows(mart, other)).isEqualTo(4L);
        }
    }

    @Test
    @DisplayName("묶음 크기를 넘는 건수도 빠짐없이 넣는다")
    void insertsAcrossBatchBoundaries() throws SQLException {
        int total = 2_001;
        List<BalanceRow> rows = rows(total);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows, pseudonymizer);

            assertThat(writer.countRows(mart, D)).isEqualTo(total);
            assertThat(writer.sumBalance(mart, D))
                    .isEqualTo(rows.stream().mapToLong(BalanceRow::balance).sum());
        }
    }

    @Test
    @DisplayName("행이 없으면 합계는 0이다")
    void sumIsZeroWhenNoRows() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.sumBalance(mart, D)).isZero();
            assertThat(writer.countRows(mart, D)).isZero();
        }
    }

    private static List<BalanceRow> rows(int count) {
        List<BalanceRow> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new BalanceRow(i, 1_000_000L + i, "DEMAND_DEPOSIT", null, null, 100L * i));
        }
        return rows;
    }
}
