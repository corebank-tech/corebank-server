package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.domain.MartBalanceRow;
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

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("받은 키와 값을 그대로 마트 컬럼에 쓴다")
    void writesGivenValues() throws SQLException {
        String accountKey = "a".repeat(64);
        String customerKey = "b".repeat(64);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(
                    mart,
                    1L,
                    D,
                    List.of(new MartBalanceRow(accountKey, customerKey, "DEMAND_DEPOSIT", null, null, 500L)));

            try (PreparedStatement statement = mart.prepareStatement(
                            "SELECT account_key, customer_key, account_type, product_id, product_group, balance,"
                                    + " load_batch_id FROM mart_daily_account_balance");
                    ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("account_key")).isEqualTo(accountKey);
                assertThat(resultSet.getString("customer_key")).isEqualTo(customerKey);
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
    @DisplayName("상품 계좌의 상품 ID와 그룹을 쓴다")
    void writesProductColumns() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(
                    mart,
                    1L,
                    D,
                    List.of(new MartBalanceRow("a".repeat(64), "b".repeat(64), "TIME_DEPOSIT", 100L, "DEPOSIT", 700L)));

            try (PreparedStatement statement =
                            mart.prepareStatement("SELECT product_id, product_group FROM mart_daily_account_balance");
                    ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("product_id")).isEqualTo(100L);
                assertThat(resultSet.getString("product_group")).isEqualTo("DEPOSIT");
            }
        }
    }

    @Test
    @DisplayName("지우고 다시 넣어도 건수와 합계가 같다")
    void reloadKeepsCountAndSum() throws SQLException {
        List<MartBalanceRow> rows = rows(3);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows);
            long countBefore = writer.countRows(mart, D);
            long sumBefore = writer.sumBalance(mart, D);

            int deleted = writer.deleteByBaseDate(mart, D);
            writer.insert(mart, 2L, D, rows);

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
            writer.insert(mart, 1L, D, rows(2));
            writer.insert(mart, 2L, other, rows(4));

            writer.deleteByBaseDate(mart, D);

            assertThat(writer.countRows(mart, D)).isZero();
            assertThat(writer.countRows(mart, other)).isEqualTo(4L);
        }
    }

    @Test
    @DisplayName("묶음 크기를 넘는 건수도 빠짐없이 넣는다")
    void insertsAcrossBatchBoundaries() throws SQLException {
        int total = 2_001;
        List<MartBalanceRow> rows = rows(total);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows);

            assertThat(writer.countRows(mart, D)).isEqualTo(total);
            assertThat(writer.sumBalance(mart, D))
                    .isEqualTo(rows.stream().mapToLong(MartBalanceRow::balance).sum());
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

    private static List<MartBalanceRow> rows(int count) {
        List<MartBalanceRow> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String key = String.format("%064x", i);
            rows.add(new MartBalanceRow(
                    key, String.format("%064x", 1_000_000L + i), "DEMAND_DEPOSIT", null, null, 100L * i));
        }
        return rows;
    }
}
