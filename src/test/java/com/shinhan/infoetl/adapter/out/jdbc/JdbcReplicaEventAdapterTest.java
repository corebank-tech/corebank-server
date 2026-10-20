package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcReplicaEventAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final JdbcReplicaEventAdapter adapter = new JdbcReplicaEventAdapter(InfoEtlTestDatabase::replica);

    @BeforeEach
    void resetReplica() throws SQLException {
        InfoEtlTestDatabase.resetReplica();
        ReplicaFixtures.insertProduct(100L, "DEPOSIT");
    }

    @Test
    @DisplayName("가입 논리 영업일이 기준일인 성공 가입을 SUBSCRIBE 이벤트로 읽는다")
    void readsSubscriptionOnBaseDate() throws SQLException {
        subscribe(10L, 21L, 201L, "SUCCESS", D);

        List<SubscriptionEventRow> events = adapter.readSubscriptionEvents(D);

        assertThat(events).hasSize(1);
        SubscriptionEventRow event = events.get(0);
        assertThat(event.eventType()).isEqualTo(EventType.SUBSCRIBE);
        assertThat(event.accountId()).isEqualTo(201L);
        assertThat(event.customerId()).isEqualTo(21L);
        assertThat(event.productId()).isEqualTo(100L);
        assertThat(event.productCode()).isEqualTo("P100");
        assertThat(event.productName()).isEqualTo("상품100");
        assertThat(event.productGroup()).isEqualTo("DEPOSIT");
    }

    @Test
    @DisplayName("초입금 원장이 없는 정기적금 가입도 읽는다")
    void readsSubscriptionWithoutLedger() throws SQLException {
        ReplicaFixtures.insertProduct(101L, "SAVINGS");
        ReplicaFixtures.insertAccount(202L, 22L, 101L, "INSTALLMENT_SAVINGS", "ACTIVE");
        ReplicaFixtures.insertSubscription(11L, 22L, 101L, 202L, "SUCCESS", D);

        List<SubscriptionEventRow> events = adapter.readSubscriptionEvents(D);

        assertThat(events).extracting(SubscriptionEventRow::accountId).containsExactly(202L);
        assertThat(events.get(0).productGroup()).isEqualTo("SAVINGS");
    }

    @Test
    @DisplayName("COB 전환 뒤 마감 중에 들어온 가입은 달력 날짜가 아니라 논리 영업일의 SUBSCRIBE로 읽는다")
    void readsSubscriptionAfterCobRolloverOnLogicalBusinessDay() throws SQLException {
        // 10/19 23:40에 가입했지만 COB가 이미 영업일을 10/20으로 넘긴 뒤라 논리 영업일은 10/20이다.
        ReplicaFixtures.insertAccount(201L, 21L, 100L, "TIME_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertSubscriptionAt(10L, 21L, 100L, 201L, LocalDateTime.of(2026, 10, 19, 23, 40), D);

        assertThat(adapter.readSubscriptionEvents(D))
                .extracting(SubscriptionEventRow::accountId)
                .containsExactly(201L);
        assertThat(adapter.readSubscriptionEvents(D.minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("가입 논리 영업일이 다른 날이거나 비어 있으면 읽지 않는다")
    void ignoresSubscriptionOnOtherOrMissingDate() throws SQLException {
        subscribe(10L, 21L, 201L, "SUCCESS", D.minusDays(1));
        subscribe(11L, 22L, 202L, "SUCCESS", D.plusDays(1));
        subscribe(12L, 23L, 203L, "SUCCESS", null);

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("성공이 아닌 가입 건(ERROR, PROCESSING)은 읽지 않는다")
    void ignoresNonSuccessfulSubscriptions() throws SQLException {
        subscribe(10L, 21L, 201L, "ERROR", D);
        subscribe(11L, 22L, 202L, "PROCESSING", D);

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("해지 논리 영업일이 기준일인 상품 계좌를 원장 없이도 CLOSE 이벤트로 읽는다")
    void readsCloseWithoutLedger() throws SQLException {
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", D);

        List<SubscriptionEventRow> events = adapter.readSubscriptionEvents(D);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(EventType.CLOSE);
        assertThat(events.get(0).accountId()).isEqualTo(300L);
        assertThat(events.get(0).customerId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("해지 출금 원장의 거래일이 해지 논리 영업일과 같으면 정상으로 읽는다")
    void readsCloseWhenLedgerMatches() throws SQLException {
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", D);
        ledger(30L, 300L, LocalDateTime.of(2026, 10, 20, 9, 6), D);

        assertThat(adapter.readSubscriptionEvents(D)).hasSize(1);
    }

    @Test
    @DisplayName("원장 거래일이 비어 있어도 영업일로 올린 날짜가 해지 영업일과 같으면 정상이다")
    void rollsNullLedgerTradeDateBeforeComparing() throws SQLException {
        LocalDate monday = LocalDate.of(2026, 10, 19);
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", monday);
        ledger(30L, 300L, LocalDateTime.of(2026, 10, 17, 10, 0), null); // 토요일 기표는 월요일 거래

        assertThat(adapter.readSubscriptionEvents(monday)).hasSize(1);
    }

    @Test
    @DisplayName("해지 출금 원장의 마지막 거래일이 해지 논리 영업일과 다르면 원천 계약 위반으로 거부한다")
    void rejectsCloseWhenLedgerDiffers() throws SQLException {
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", D);
        ledger(30L, 300L, LocalDateTime.of(2026, 10, 21, 9, 6), D.plusDays(1));

        assertThatThrownBy(() -> adapter.readSubscriptionEvents(D))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accountId=300")
                .hasMessageContaining("closedTradeDate=2026-10-20")
                .hasMessageContaining("lastLedgerDate=2026-10-21");
    }

    @Test
    @DisplayName("다른 날 해지된 계좌의 불일치는 이 기준일 적재를 막지 않는다")
    void ignoresMismatchOfOtherDay() throws SQLException {
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", D.minusDays(1));
        ledger(30L, 300L, LocalDateTime.of(2026, 10, 21, 9, 6), D.plusDays(1));

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("해지 논리 영업일이 기준일이 아니거나 활성 계좌와 입출금 계좌는 CLOSE가 아니다")
    void ignoresNonCloseAccounts() throws SQLException {
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", D.minusDays(1));
        ReplicaFixtures.insertAccount(301L, 32L, 100L, "TIME_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertClosedAccount(302L, 33L, null, "DEMAND_DEPOSIT", D);

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("가입 이벤트가 먼저, 해지 이벤트가 나중에 계좌 순으로 나온다")
    void ordersSubscribeBeforeCloseByAccount() throws SQLException {
        subscribe(11L, 22L, 202L, "SUCCESS", D);
        subscribe(10L, 21L, 201L, "SUCCESS", D);
        ReplicaFixtures.insertClosedAccount(300L, 31L, 100L, "TIME_DEPOSIT", D);

        assertThat(adapter.readSubscriptionEvents(D))
                .extracting(SubscriptionEventRow::eventType, SubscriptionEventRow::accountId)
                .containsExactly(
                        Tuple.tuple(EventType.SUBSCRIBE, 201L),
                        Tuple.tuple(EventType.SUBSCRIBE, 202L),
                        Tuple.tuple(EventType.CLOSE, 300L));
    }

    private void subscribe(long subscriptionId, long customerId, long accountId, String status, LocalDate tradeDate)
            throws SQLException {
        ReplicaFixtures.insertAccount(accountId, customerId, 100L, "TIME_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertSubscription(subscriptionId, customerId, 100L, accountId, status, tradeDate);
    }

    private void ledger(long ledgerEntryId, long accountId, LocalDateTime occurredAt, LocalDate tradeDate)
            throws SQLException {
        ReplicaFixtures.insertLedgerDetailed(
                ledgerEntryId,
                accountId,
                "TX000000000000000" + ledgerEntryId,
                "WITHDRAWAL",
                0L,
                "TERMINATION",
                occurredAt,
                tradeDate);
    }
}
