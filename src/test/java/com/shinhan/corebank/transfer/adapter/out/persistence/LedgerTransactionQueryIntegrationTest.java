package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.api.LedgerTransactionQuery;
import com.shinhan.corebank.transfer.api.LedgerTransactionSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("원장 거래 조회 계약(LedgerTransactionQuery) 통합 테스트")
class LedgerTransactionQueryIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 원장 ID·거래일 대역(2031-04).
    private static final long ID_FROM = 928_100_001L;
    private static final long ID_TO = 928_100_999L;
    private static final LocalDate TRADE_DATE = LocalDate.of(2031, 4, 15);
    private static final long ACCOUNT_A = 928_100_001L;
    private static final long ACCOUNT_B = 928_100_002L;

    @Autowired
    private LedgerTransactionQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long nextId = ID_FROM;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE ledger_entry_id BETWEEN ? AND ?", ID_FROM, ID_TO);
    }

    @Test
    @DisplayName("출금·입금 2행 거래는 한 줄이고 금액은 합산하지 않는다. 1행 거래도 한 줄이다")
    void oneRowPerTransactionWithoutSumming() {
        insertPair("20310415WB0000000001", "IMMEDIATE_TRANSFER", 5_000L, TRADE_DATE, at(TRADE_DATE, 10));
        insertRow("20310415BT0000000002", "INTEREST", "DEPOSIT", 70L, TRADE_DATE, at(TRADE_DATE, 11));

        assertThat(query.findByTradeDate(TRADE_DATE, null, 100))
                .extracting(
                        LedgerTransactionSummary::transactionNumber,
                        LedgerTransactionSummary::transactionType,
                        LedgerTransactionSummary::tradeDate,
                        LedgerTransactionSummary::amount)
                .containsExactly(
                        tuple("20310415BT0000000002", "INTEREST", TRADE_DATE, 70L),
                        tuple("20310415WB0000000001", "IMMEDIATE_TRANSFER", TRADE_DATE, 5_000L));
    }

    @Test
    @DisplayName("다른 거래일, 거래일 NULL, 개시·QA 시드 행은 내지 않는다")
    void excludesOtherDatesNullAndNonVoucherTypes() {
        insertPair("20310415WB0000000001", "IMMEDIATE_TRANSFER", 1_000L, TRADE_DATE, at(TRADE_DATE, 10));
        insertPair("20310415WB0000000002", "IMMEDIATE_TRANSFER", 1_000L, TRADE_DATE.plusDays(1), at(TRADE_DATE, 23));
        insertPair("20310415WB0000000003", "IMMEDIATE_TRANSFER", 1_000L, null, at(TRADE_DATE, 12));
        insertRow("20310415BT0000000004", "OPENING", "DEPOSIT", 1_000L, TRADE_DATE, at(TRADE_DATE, 0));
        insertRow("20310415BT0000000005", "QA_SEED_INITIAL", "DEPOSIT", 1_000L, TRADE_DATE, at(TRADE_DATE, 0));

        assertThat(query.findByTradeDate(TRADE_DATE, null, 100))
                .extracting(LedgerTransactionSummary::transactionNumber)
                .containsExactly("20310415WB0000000001");
    }

    @Test
    @DisplayName("연휴 앞에 발생한 거래와 COB 가 늦어 다음 날 발생한 거래도 그 거래일로 읽는다")
    void includesHolidayAndLateCobTransactions() {
        LocalDate holidayStart = TRADE_DATE.minusDays(5);
        insertPair("20310410WB0000000001", "SCHEDULED_TRANSFER", 1_000L, TRADE_DATE, at(holidayStart, 9));
        LocalDate nextDay = TRADE_DATE.plusDays(1);
        insertPair(
                "20310416WB0000000001",
                "AUTO_TRANSFER",
                2_000L,
                TRADE_DATE,
                at(nextDay, 0).plusMinutes(30));

        assertThat(query.findByTradeDate(TRADE_DATE, null, 100))
                .extracting(LedgerTransactionSummary::transactionNumber)
                .containsExactly("20310410WB0000000001", "20310416WB0000000001");
    }

    @Test
    @DisplayName("거래번호 순으로 limit 만큼 읽고, 마지막 거래번호를 커서로 넘기면 다음 묶음을 읽는다")
    void pagesByCursor() {
        insertPair("20310415WB0000000003", "IMMEDIATE_TRANSFER", 3L, TRADE_DATE, at(TRADE_DATE, 10));
        insertPair("20310415WB0000000001", "IMMEDIATE_TRANSFER", 1L, TRADE_DATE, at(TRADE_DATE, 10));
        insertPair("20310415WB0000000002", "IMMEDIATE_TRANSFER", 2L, TRADE_DATE, at(TRADE_DATE, 10));

        List<LedgerTransactionSummary> first = query.findByTradeDate(TRADE_DATE, null, 2);
        List<LedgerTransactionSummary> second =
                query.findByTradeDate(TRADE_DATE, first.getLast().transactionNumber(), 2);

        assertThat(first)
                .extracting(LedgerTransactionSummary::transactionNumber)
                .containsExactly("20310415WB0000000001", "20310415WB0000000002");
        assertThat(second)
                .extracting(LedgerTransactionSummary::transactionNumber)
                .containsExactly("20310415WB0000000003");
    }

    private static LocalDateTime at(LocalDate date, int hour) {
        return date.atTime(hour, 0);
    }

    private void insertPair(
            String transactionNumber, String type, long amount, LocalDate tradeDate, LocalDateTime occurredAt) {
        insertRow(transactionNumber, type, "WITHDRAWAL", amount, tradeDate, occurredAt, ACCOUNT_A);
        insertRow(transactionNumber, type, "DEPOSIT", amount, tradeDate, occurredAt, ACCOUNT_B);
    }

    private void insertRow(
            String transactionNumber,
            String type,
            String direction,
            long amount,
            LocalDate tradeDate,
            LocalDateTime occurredAt) {
        insertRow(transactionNumber, type, direction, amount, tradeDate, occurredAt, ACCOUNT_B);
    }

    private void insertRow(
            String transactionNumber,
            String type,
            String direction,
            long amount,
            LocalDate tradeDate,
            LocalDateTime occurredAt,
            long accountId) {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction, amount,
                                          balance_after, transaction_type, transaction_content, channel, reversed,
                                          reversal_id, occurred_at, trade_date)
                VALUES (?, ?, NULL, ?, ?, ?, 0, ?, 'PH28B', ?, FALSE, NULL, ?, ?)
                """,
                nextId++,
                accountId,
                transactionNumber,
                direction,
                amount,
                type,
                transactionNumber.substring(8, 10),
                occurredAt,
                tradeDate);
    }
}
