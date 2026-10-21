package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.MartEventRow;
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

class MartEventWriterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final MartEventWriter writer = new MartEventWriter();

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("받은 이벤트를 그대로 마트 컬럼에 쓴다")
    void writesGivenValues() throws SQLException {
        MartEventRow row =
                new MartEventRow(EventType.CLOSE, key('a'), key('b'), 100L, "DEP-12M", "쏠편한 정기예금", "DEPOSIT");
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 9L, D, List.of(row));

            try (PreparedStatement statement = mart.prepareStatement(
                            "SELECT base_date, event_type, account_key, customer_key, product_id, product_code,"
                                    + " product_name, product_group, load_batch_id FROM mart_product_subscription_event");
                    ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getObject("base_date", LocalDate.class)).isEqualTo(D);
                assertThat(resultSet.getString("event_type")).isEqualTo("CLOSE");
                assertThat(resultSet.getString("account_key")).isEqualTo(key('a'));
                assertThat(resultSet.getString("customer_key")).isEqualTo(key('b'));
                assertThat(resultSet.getLong("product_id")).isEqualTo(100L);
                assertThat(resultSet.getString("product_code")).isEqualTo("DEP-12M");
                assertThat(resultSet.getString("product_name")).isEqualTo("쏠편한 정기예금");
                assertThat(resultSet.getString("product_group")).isEqualTo("DEPOSIT");
                assertThat(resultSet.getLong("load_batch_id")).isEqualTo(9L);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("지우고 다시 넣어도 건수가 같다")
    void reloadKeepsCount() throws SQLException {
        List<MartEventRow> rows = rows(3, EventType.SUBSCRIBE);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows);

            int deleted = writer.deleteByBaseDate(mart, D);
            writer.insert(mart, 2L, D, rows);

            assertThat(deleted).isEqualTo(3);
            assertThat(writer.countRows(mart, D)).isEqualTo(3L);
        }
    }

    @Test
    @DisplayName("삭제는 해당 기준일 이벤트만 지운다")
    void deletesOnlyTargetDate() throws SQLException {
        LocalDate other = D.plusDays(1);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows(2, EventType.SUBSCRIBE));
            writer.insert(mart, 2L, other, rows(4, EventType.CLOSE));

            writer.deleteByBaseDate(mart, D);

            assertThat(writer.countRows(mart, D)).isZero();
            assertThat(writer.countRows(mart, other)).isEqualTo(4L);
        }
    }

    @Test
    @DisplayName("묶음 크기를 넘는 건수도 빠짐없이 넣는다")
    void insertsAcrossBatchBoundaries() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, rows(2_001, EventType.SUBSCRIBE));

            assertThat(writer.countRows(mart, D)).isEqualTo(2_001L);
        }
    }

    @Test
    @DisplayName("같은 계좌의 같은 종류 이벤트를 다른 기준일에 또 넣으면 DB가 막는다")
    void rejectsSameAccountSameEventOnAnotherDate() throws SQLException {
        MartEventRow row = new MartEventRow(EventType.CLOSE, key('a'), key('b'), 100L, "P100", "상품", "DEPOSIT");
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(mart, 1L, D, List.of(row));

            assertThatThrownBy(() -> writer.insert(mart, 2L, D.plusDays(1), List.of(row)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_mpse_account_event");
        }
    }

    @Test
    @DisplayName("같은 계좌라도 가입과 해지는 각각 한 번씩 들어간다")
    void allowsSubscribeAndCloseForSameAccount() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            writer.insert(
                    mart,
                    1L,
                    D,
                    List.of(new MartEventRow(EventType.SUBSCRIBE, key('a'), key('b'), 100L, "P100", "상품", "DEPOSIT")));
            writer.insert(
                    mart,
                    2L,
                    D.plusDays(30),
                    List.of(new MartEventRow(EventType.CLOSE, key('a'), key('b'), 100L, "P100", "상품", "DEPOSIT")));

            assertThat(writer.countRows(mart, D)).isEqualTo(1L);
            assertThat(writer.countRows(mart, D.plusDays(30))).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("행이 없으면 건수는 0이다")
    void countIsZeroWhenNoRows() throws SQLException {
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(writer.countRows(mart, D)).isZero();
        }
    }

    private static String key(char fill) {
        return String.valueOf(fill).repeat(64);
    }

    private static List<MartEventRow> rows(int count, EventType type) {
        List<MartEventRow> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new MartEventRow(
                    type,
                    String.format("%064x", i),
                    String.format("%064x", 1_000_000L + i),
                    100L,
                    "P100",
                    "상품",
                    "DEPOSIT"));
        }
        return rows;
    }
}
