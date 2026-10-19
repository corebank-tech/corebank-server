package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcMartLoadAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T16:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final JdbcMartLoadAdapter adapter = new JdbcMartLoadAdapter(InfoEtlTestDatabase::mart, CLOCK);
    private final MartBalanceWriter balanceWriter = new MartBalanceWriter();
    private final MartEventWriter eventWriter = new MartEventWriter();

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("잔액과 이벤트를 함께 쓰고 건수·합계를 적재 기록에 남긴다")
    void publishesBalancesAndEventsTogether() throws SQLException {
        long id = adapter.start(D, Duration.ofHours(6));

        LoadResult result = adapter.publish(
                id, D, List.of(balance('a', 300L), balance('b', 700L)), List.of(event('a')), dates -> D);

        assertThat(result.balanceRowCount()).isEqualTo(2L);
        assertThat(result.eventRowCount()).isEqualTo(1L);
        assertThat(result.martBalanceTotal()).isEqualTo(1_000L);
        try (Connection mart = InfoEtlTestDatabase.mart();
                PreparedStatement statement =
                        mart.prepareStatement("SELECT status, balance_row_count, event_row_count, mart_balance_total"
                                + " FROM mart_load_batch WHERE load_batch_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("status")).isEqualTo("COMPLETED");
                assertThat(row.getLong("balance_row_count")).isEqualTo(2L);
                assertThat(row.getLong("event_row_count")).isEqualTo(1L);
                assertThat(row.getLong("mart_balance_total")).isEqualTo(1_000L);
            }
        }
    }

    @Test
    @DisplayName("이벤트가 없어도 잔액은 적재되고 이벤트 건수는 0이다")
    void publishesBalancesWithoutEvents() {
        long id = adapter.start(D, Duration.ofHours(6));

        LoadResult result = adapter.publish(id, D, List.of(balance('a', 300L)), List.of(), dates -> D);

        assertThat(result.balanceRowCount()).isEqualTo(1L);
        assertThat(result.eventRowCount()).isZero();
    }

    @Test
    @DisplayName("재적재하면 같은 기준일의 이벤트도 새 값으로 교체한다")
    void reloadReplacesEvents() throws SQLException {
        long first = adapter.start(D, Duration.ofHours(6));
        adapter.publish(first, D, List.of(balance('a', 300L)), List.of(event('a'), event('b')), dates -> D);
        long second = adapter.start(D, Duration.ofHours(6));

        LoadResult result = adapter.publish(second, D, List.of(balance('a', 300L)), List.of(event('c')), dates -> D);

        assertThat(result.eventRowCount()).isEqualTo(1L);
        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(eventWriter.countRows(mart, D)).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("같은 계좌의 이벤트가 다른 기준일에 이미 있으면 안내와 함께 실패하고 전부 되돌린다")
    void eventDateConflictFailsWithGuidanceAndRollsBack() throws SQLException {
        LocalDate earlier = D.minusDays(3);
        long firstId = adapter.start(earlier, Duration.ofHours(6));
        adapter.publish(firstId, earlier, List.of(balance('a', 100L)), List.of(event('a')), dates -> D);
        long secondId = adapter.start(D, Duration.ofHours(6));

        assertThatThrownBy(() ->
                        adapter.publish(secondId, D, List.of(balance('a', 300L)), List.of(event('a')), dates -> D))
                .isInstanceOf(EtlStorageException.class)
                .hasMessageContaining("먼저 재적재");

        try (Connection mart = InfoEtlTestDatabase.mart()) {
            assertThat(balanceWriter.countRows(mart, D)).isZero();
            assertThat(eventWriter.countRows(mart, D)).isZero();
            assertThat(eventWriter.countRows(mart, earlier)).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("fail은 FAILED와 사유를 기록한다")
    void failRecordsReason() throws SQLException {
        long id = adapter.start(D, Duration.ofHours(6));

        adapter.fail(id, "boom");

        try (Connection mart = InfoEtlTestDatabase.mart();
                PreparedStatement statement = mart.prepareStatement(
                        "SELECT status, failure_reason FROM mart_load_batch WHERE load_batch_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("status")).isEqualTo("FAILED");
                assertThat(row.getString("failure_reason")).isEqualTo("boom");
            }
        }
    }

    private static MartBalanceRow balance(char fill, long amount) {
        return new MartBalanceRow(key(fill), key('z'), "DEMAND_DEPOSIT", null, null, amount);
    }

    private static MartEventRow event(char fill) {
        return new MartEventRow(EventType.CLOSE, key(fill), key('z'), 100L, "P100", "상품", "DEPOSIT");
    }

    private static String key(char fill) {
        return String.valueOf(fill).repeat(64);
    }
}
