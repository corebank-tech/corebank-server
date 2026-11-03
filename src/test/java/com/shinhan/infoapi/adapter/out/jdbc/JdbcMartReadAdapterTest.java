package com.shinhan.infoapi.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoapi.domain.DepositBalancePoint;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import com.shinhan.infoetl.InfoEtlTestDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 실제 마트 스키마에 값을 넣고 어댑터의 SQL이 설계서 §5 규칙대로 읽는지 본다.
class JdbcMartReadAdapterTest {

    private final JdbcMartReadAdapter adapter = new JdbcMartReadAdapter(InfoEtlTestDatabase::mart);

    @BeforeEach
    void resetMart() throws SQLException {
        InfoEtlTestDatabase.resetMart();
    }

    @Test
    @DisplayName("적재 기록이 없으면 asOf가 비어 있다")
    void asOfIsEmptyWithoutBatches() {
        assertThat(adapter.latestAsOf()).isEmpty();
    }

    @Test
    @DisplayName("asOf는 completed_at이 가장 늦은 COMPLETED 배치의 as_of_date다")
    void asOfComesFromLatestCompletedBatch() throws SQLException {
        batch("2026-10-20", "COMPLETED", "2026-10-21 03:30:00", 100L, "2026-10-20");
        batch("2026-10-21", "COMPLETED", "2026-10-22 03:30:00", 200L, "2026-10-21");
        batch("2026-10-22", "FAILED", "2026-10-23 03:30:00", null, null);
        batch("2026-10-23", "RUNNING", null, null, null);

        assertThat(adapter.latestAsOf()).contains(LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("가장 최근 COMPLETED 배치의 as_of_date가 NULL이면 오래된 값으로 대신하지 않고 이력 없음이다")
    void latestBatchWithNullWatermarkMeansNoHistory() throws SQLException {
        batch("2026-10-20", "COMPLETED", "2026-10-21 03:30:00", 100L, "2026-10-20");
        batch("2026-10-21", "COMPLETED", "2026-10-22 03:30:00", 200L, null);

        assertThat(adapter.latestAsOf()).isEmpty();
    }

    @Test
    @DisplayName("일별 잔액은 범위 안의 COMPLETED 배치만 날짜 오름차순으로 돌려준다")
    void depositBalancesReadsCompletedBatchesInRange() throws SQLException {
        batch("2026-10-19", "COMPLETED", "2026-10-20 03:30:00", 90L, "2026-10-19");
        batch("2026-10-20", "REPLACED", "2026-10-21 03:30:00", 111L, "2026-10-20");
        batch("2026-10-20", "COMPLETED", "2026-10-21 04:30:00", 100L, "2026-10-20");
        batch("2026-10-21", "FAILED", "2026-10-22 03:30:00", 999L, null);
        batch("2026-10-22", "COMPLETED", "2026-10-23 03:30:00", 300L, "2026-10-22");
        batch("2026-10-23", "COMPLETED", "2026-10-24 03:30:00", 400L, "2026-10-23");

        List<DepositBalancePoint> points =
                adapter.depositBalances(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));

        assertThat(points)
                .containsExactly(
                        new DepositBalancePoint(LocalDate.of(2026, 10, 20), 100L),
                        new DepositBalancePoint(LocalDate.of(2026, 10, 22), 300L));
    }

    @Test
    @DisplayName("일별 잔액은 mart_balance_total이 NULL인 배치를 뺀다")
    void depositBalancesSkipsNullTotal() throws SQLException {
        batch("2026-10-20", "COMPLETED", "2026-10-21 03:30:00", null, "2026-10-20");

        assertThat(adapter.depositBalances(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .isEmpty();
    }

    @Test
    @DisplayName("가입·해지는 범위 안의 이벤트를 상품별로 센다")
    void productSubscriptionsCountsEventsPerProduct() throws SQLException {
        event("2026-10-05", "SUBSCRIBE", 'a', 3, "DEP-12M", "쏠편한 정기예금", "DEPOSIT");
        event("2026-10-06", "SUBSCRIBE", 'b', 3, "DEP-12M", "쏠편한 정기예금", "DEPOSIT");
        event("2026-10-07", "CLOSE", 'a', 3, "DEP-12M", "쏠편한 정기예금", "DEPOSIT");
        event("2026-10-08", "SUBSCRIBE", 'c', 4, "SAV-12M", "쏠편한 정기적금", "SAVINGS");
        event("2026-09-30", "SUBSCRIBE", 'd', 4, "SAV-12M", "쏠편한 정기적금", "SAVINGS");
        event("2026-11-01", "SUBSCRIBE", 'e', 4, "SAV-12M", "쏠편한 정기적금", "SAVINGS");

        List<ProductSubscriptionLine> lines =
                adapter.productSubscriptions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(lines)
                .containsExactly(
                        new ProductSubscriptionLine(3, "DEP-12M", "쏠편한 정기예금", "DEPOSIT", 2, 1),
                        new ProductSubscriptionLine(4, "SAV-12M", "쏠편한 정기적금", "SAVINGS", 1, 0));
    }

    @Test
    @DisplayName("상품명이 바뀌었으면 조회 범위와 상관없이 가장 최근 이벤트의 이름을 쓴다")
    void productNameComesFromMostRecentEventOfProduct() throws SQLException {
        event("2026-10-05", "SUBSCRIBE", 'a', 3, "DEP-12M", "쏠편한 정기예금", "DEPOSIT");
        event("2026-11-20", "SUBSCRIBE", 'b', 3, "DEP-12M", "쏠편한 정기예금 Plus", "DEPOSIT");

        List<ProductSubscriptionLine> lines =
                adapter.productSubscriptions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).productName()).isEqualTo("쏠편한 정기예금 Plus");
        assertThat(lines.get(0).subscribedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("과거 날짜를 나중에 재적재했으면 날짜가 더 늦은 행이 아니라 가장 최근에 적재된 행의 이름을 쓴다")
    void productNameComesFromMostRecentlyLoadedRow() throws SQLException {
        event(1, "2026-11-20", "SUBSCRIBE", 'a', 3, "DEP-12M", "옛 이름", "DEPOSIT");
        event(2, "2026-10-05", "SUBSCRIBE", 'b', 3, "DEP-12M", "새 이름", "DEPOSIT");

        List<ProductSubscriptionLine> lines =
                adapter.productSubscriptions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).productName()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("같은 날 이름이 둘이면 기본키 순서로 한 행만 골라 결과가 항상 같다")
    void sameDayNameConflictIsDeterministic() throws SQLException {
        event("2026-10-05", "SUBSCRIBE", 'a', 3, "DEP-12M", "이름A", "DEPOSIT");
        event("2026-10-05", "SUBSCRIBE", 'b', 3, "DEP-12M", "이름B", "DEPOSIT");

        List<ProductSubscriptionLine> first =
                adapter.productSubscriptions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        List<ProductSubscriptionLine> second =
                adapter.productSubscriptions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(first).hasSize(1);
        assertThat(first.get(0).productName()).isEqualTo("이름B");
        assertThat(second).isEqualTo(first);
    }

    private static void batch(String baseDate, String status, String completedAt, Long total, String asOf)
            throws SQLException {
        execute("INSERT INTO mart_load_batch (base_date, status, started_at, completed_at, mart_balance_total,"
                + " as_of_date) VALUES ('" + baseDate + "', '" + status + "', '2026-10-01 00:00:00.000000', "
                + (completedAt == null ? "NULL" : "'" + completedAt + "'") + ", "
                + (total == null ? "NULL" : total.toString()) + ", "
                + (asOf == null ? "NULL" : "'" + asOf + "'") + ")");
    }

    private static void event(
            String baseDate, String eventType, char keyChar, long productId, String code, String name, String group)
            throws SQLException {
        event(1, baseDate, eventType, keyChar, productId, code, name, group);
    }

    private static void event(
            long loadBatchId,
            String baseDate,
            String eventType,
            char keyChar,
            long productId,
            String code,
            String name,
            String group)
            throws SQLException {
        String key = String.valueOf(keyChar).repeat(64);
        execute("INSERT INTO mart_product_subscription_event (base_date, event_type, account_key, customer_key,"
                + " product_id, product_code, product_name, product_group, load_batch_id) VALUES ('" + baseDate
                + "', '" + eventType + "', '" + key + "', '" + key + "', " + productId + ", '" + code + "', '"
                + name + "', '" + group + "', " + loadBatchId + ")");
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.mart();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
