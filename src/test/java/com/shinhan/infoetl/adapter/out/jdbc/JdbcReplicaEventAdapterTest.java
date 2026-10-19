package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        ReplicaFixtures.insertAccount(1L, 11L, null, "DEMAND_DEPOSIT", "ACTIVE");
    }

    @Test
    @DisplayName("거래일이 기준일인 가입 건을 SUBSCRIBE 이벤트로 읽는다")
    void readsSubscriptionOnBaseDate() throws SQLException {
        subscribe(10L, 21L, 201L, "TX00000000000000001", "SUCCESS", LocalDateTime.of(2026, 10, 20, 10, 0), D);

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
    @DisplayName("거래일이 다른 날인 가입 건은 읽지 않는다")
    void ignoresSubscriptionOnOtherDate() throws SQLException {
        subscribe(
                10L,
                21L,
                201L,
                "TX00000000000000001",
                "SUCCESS",
                LocalDateTime.of(2026, 10, 19, 10, 0),
                D.minusDays(1));
        subscribe(
                11L, 22L, 202L, "TX00000000000000002", "SUCCESS", LocalDateTime.of(2026, 10, 21, 10, 0), D.plusDays(1));

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("성공이 아닌 가입 건(ERROR, PROCESSING)은 읽지 않는다")
    void ignoresNonSuccessfulSubscriptions() throws SQLException {
        subscribe(10L, 21L, 201L, "TX00000000000000001", "ERROR", LocalDateTime.of(2026, 10, 20, 10, 0), D);
        subscribe(11L, 22L, 202L, "TX00000000000000002", "PROCESSING", LocalDateTime.of(2026, 10, 20, 10, 0), D);

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("거래일이 비어 있으면 발생일을 거래일로 본다")
    void usesOccurredDateWhenTradeDateIsNull() throws SQLException {
        subscribe(10L, 21L, 201L, "TX00000000000000001", "SUCCESS", LocalDateTime.of(2026, 10, 20, 23, 0), null);

        assertThat(adapter.readSubscriptionEvents(D)).hasSize(1);
    }

    @Test
    @DisplayName("발생일과 거래일이 많이 떨어져 있어도 거래일로 찾는다")
    void findsSubscriptionWhoseTradeDateIsFarFromOccurredDate() throws SQLException {
        // 연휴로 거래일이 발생일보다 열흘 뒤다. 원장 날짜 창을 쓰면 놓치는 경우다.
        subscribe(10L, 21L, 201L, "TX00000000000000001", "SUCCESS", LocalDateTime.of(2026, 10, 9, 10, 0), D);

        assertThat(adapter.readSubscriptionEvents(D)).hasSize(1);
    }

    @Test
    @DisplayName("출금 행(상대 계좌)은 가입 이벤트로 중복 집계하지 않는다")
    void countsSubscriptionOnlyOnce() throws SQLException {
        subscribe(10L, 21L, 201L, "TX00000000000000001", "SUCCESS", LocalDateTime.of(2026, 10, 20, 10, 0), D);
        ReplicaFixtures.insertLedgerDetailed(
                9L,
                1L,
                "TX00000000000000001",
                "WITHDRAWAL",
                0L,
                "PRODUCT_SUBSCRIPTION",
                LocalDateTime.of(2026, 10, 20, 10, 0),
                D);

        assertThat(adapter.readSubscriptionEvents(D)).hasSize(1);
    }

    @Test
    @DisplayName("해지 계좌의 전체 원장 마지막 거래일이 기준일이면 CLOSE 이벤트로 읽는다")
    void readsCloseWhenLastLedgerDateIsBaseDate() throws SQLException {
        closedAccount(300L, 31L, LocalDateTime.of(2026, 10, 20, 15, 0), D);

        List<SubscriptionEventRow> events = adapter.readSubscriptionEvents(D);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(EventType.CLOSE);
        assertThat(events.get(0).accountId()).isEqualTo(300L);
        assertThat(events.get(0).customerId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("해지 기표가 기준일보다 훨씬 뒤여도 그 날짜의 CLOSE로 읽는다")
    void readsCloseWhoseLedgerIsFarAfterOtherDays() throws SQLException {
        LocalDate closeDay = D.plusDays(30);
        closedAccount(300L, 31L, LocalDateTime.of(2026, 11, 19, 15, 0), closeDay);

        assertThat(adapter.readSubscriptionEvents(closeDay)).hasSize(1);
        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("해지 계좌라도 마지막 거래일이 기준일이 아니면 읽지 않는다")
    void ignoresCloseWithOtherLastDate() throws SQLException {
        closedAccount(300L, 31L, LocalDateTime.of(2026, 10, 19, 15, 0), D.minusDays(1));

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("활성 계좌와 입출금 계좌는 CLOSE 이벤트가 아니다")
    void ignoresActiveAndDemandAccounts() throws SQLException {
        ReplicaFixtures.insertAccount(301L, 32L, 100L, "TIME_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertLedgerDetailed(
                70L, 301L, "TX00000000000000070", "DEPOSIT", 500L, "INTEREST", LocalDateTime.of(2026, 10, 20, 9, 0), D);
        ReplicaFixtures.insertAccount(302L, 33L, null, "DEMAND_DEPOSIT", "CLOSED");
        ReplicaFixtures.insertLedgerDetailed(
                71L,
                302L,
                "TX00000000000000071",
                "WITHDRAWAL",
                0L,
                "TERMINATION",
                LocalDateTime.of(2026, 10, 20, 9, 0),
                D);

        assertThat(adapter.readSubscriptionEvents(D)).isEmpty();
    }

    @Test
    @DisplayName("가입 이벤트가 먼저, 해지 이벤트가 나중에 계좌 순으로 나온다")
    void ordersSubscribeBeforeCloseByAccount() throws SQLException {
        subscribe(11L, 22L, 202L, "TX00000000000000002", "SUCCESS", LocalDateTime.of(2026, 10, 20, 10, 5), D);
        subscribe(10L, 21L, 201L, "TX00000000000000001", "SUCCESS", LocalDateTime.of(2026, 10, 20, 10, 0), D);
        closedAccount(300L, 31L, LocalDateTime.of(2026, 10, 20, 15, 0), D);

        assertThat(adapter.readSubscriptionEvents(D))
                .extracting(SubscriptionEventRow::eventType, SubscriptionEventRow::accountId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(EventType.SUBSCRIBE, 201L),
                        org.assertj.core.groups.Tuple.tuple(EventType.SUBSCRIBE, 202L),
                        org.assertj.core.groups.Tuple.tuple(EventType.CLOSE, 300L));
    }

    private void subscribe(
            long subscriptionId,
            long customerId,
            long accountId,
            String transactionNumber,
            String status,
            LocalDateTime occurredAt,
            LocalDate tradeDate)
            throws SQLException {
        ReplicaFixtures.insertAccount(accountId, customerId, 100L, "TIME_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertSubscription(subscriptionId, customerId, 100L, accountId, status, transactionNumber);
        ReplicaFixtures.insertSubscriptionDepositLedger(
                subscriptionId, accountId, transactionNumber, occurredAt, tradeDate);
    }

    private void closedAccount(long accountId, long customerId, LocalDateTime lastOccurredAt, LocalDate lastTradeDate)
            throws SQLException {
        ReplicaFixtures.insertAccount(accountId, customerId, 100L, "TIME_DEPOSIT", "CLOSED");
        ReplicaFixtures.insertLedgerDetailed(
                accountId * 10,
                accountId,
                "TX0000000000000" + accountId,
                "DEPOSIT",
                1_000L,
                "PRODUCT_SUBSCRIPTION",
                LocalDateTime.of(2026, 9, 1, 9, 0),
                LocalDate.of(2026, 9, 1));
        ReplicaFixtures.insertLedgerDetailed(
                accountId * 10 + 1,
                accountId,
                "TX1000000000000" + accountId,
                "WITHDRAWAL",
                0L,
                "TERMINATION",
                lastOccurredAt,
                lastTradeDate);
    }
}
