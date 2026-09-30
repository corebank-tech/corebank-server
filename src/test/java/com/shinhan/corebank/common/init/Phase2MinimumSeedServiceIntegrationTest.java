package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class Phase2MinimumSeedServiceIntegrationTest extends IntegrationTestSupport {

    private static final Phase2MinimumSeedSpec SPEC = new Phase2MinimumSeedSpec(
            4,
            12,
            20,
            3,
            2,
            8_100_001L,
            81_000_001L,
            81_000_001L,
            81_000_001L,
            81_000_001L,
            8_100_000_001L,
            "861",
            LocalDateTime.of(2026, 9, 1, 0, 0));

    @Autowired
    private Phase2MinimumSeedService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("최소 시드는 재실행해도 중복 없이 원장 쌍·잔액·개시 전표 정합성을 유지한다")
    void seedsIdempotentlyWithBalancedLedger() {
        Phase2MinimumSeedReport first = service.seed(SPEC);
        Phase2MinimumSeedReport second = service.seed(SPEC);

        assertThat(first.customers()).isEqualTo(4);
        assertThat(first.accounts()).isEqualTo(12);
        assertThat(first.ledgerEntries()).isEqualTo(20);
        assertThat(first.autoTransfers()).isEqualTo(3);
        assertThat(first.transfers()).isEqualTo(4);
        assertThat(first.elapsed()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        assertThat(second.customers()).isEqualTo(first.customers());

        assertThat(count("SELECT COUNT(*) FROM customer WHERE customer_id BETWEEN 8100001 AND 8100004"))
                .isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM account WHERE account_id BETWEEN 81000001 AND 81000012"))
                .isEqualTo(12);
        assertThat(count("SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 81000001 AND 81000020"))
                .isEqualTo(20);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 81000001 AND 81000004 AND trade_date IS NULL"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 81000001 AND 81000020 AND trade_date IS NULL"))
                .isZero();
        assertThat(count(
                        "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 81000001 AND 81000004 AND trade_date <> ?",
                        LocalDate.of(2026, 9, 1)))
                .isZero();
        assertThat(count(
                        "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 81000001 AND 81000020 AND trade_date <> ?",
                        LocalDate.of(2026, 9, 1)))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM auto_transfer WHERE auto_transfer_id BETWEEN 81000001 AND 81000003"))
                .isEqualTo(3);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN 81000001 AND 81000012 AND status = 'MATURED'"))
                .isZero();
        assertThat(count(
                        "SELECT COUNT(*) FROM account WHERE account_id BETWEEN 81000001 AND 81000012 AND maturity_date >= ? AND maturity_date < ?",
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 12, 1)))
                .isEqualTo(2);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 81000001 AND 81000004 AND (channel <> 'WB' OR transaction_number NOT LIKE '20260901WB%')"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 81000013 AND 81000020 AND (transaction_type <> 'IMMEDIATE_TRANSFER' OR channel <> 'WB')"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 81000001 AND 81000012 AND (transaction_type <> 'OPENING' OR channel <> 'BT')"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 81000001 AND 81000004 AND DATE(transferred_at) <> '2026-09-01'"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM auto_transfer WHERE auto_transfer_id BETWEEN 81000001 AND 81000003 AND (start_date <> '2026-10-01' OR next_execution_date NOT BETWEEN '2026-10-01' AND '2026-10-28')"))
                .isZero();
        String customerPassword =
                jdbc.queryForObject("SELECT password_hash FROM customer WHERE customer_id = 8100001", String.class);
        String accountPassword =
                jdbc.queryForObject("SELECT password_hash FROM account WHERE account_id = 81000001", String.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("1234", customerPassword)).isTrue();
        assertThat(encoder.matches("1234", accountPassword)).isTrue();
        assertThat(
                        count(
                                """
                        SELECT COUNT(*) FROM (
                            SELECT transfer_id FROM ledger_entry
                            WHERE transfer_id BETWEEN 81000001 AND 81000004
                            GROUP BY transfer_id
                            HAVING COUNT(*) <> 2
                               OR SUM(direction = 'WITHDRAWAL') <> 1
                               OR SUM(direction = 'DEPOSIT') <> 1
                        ) broken
                        """))
                .isZero();
        assertThat(
                        count(
                                """
                        SELECT COUNT(*) FROM account a
                        JOIN (
                            SELECT account_id, MAX(occurred_at) AS last_occurred_at
                            FROM ledger_entry
                            WHERE account_id BETWEEN 81000001 AND 81000012
                            GROUP BY account_id
                        ) latest ON latest.account_id = a.account_id
                        WHERE a.account_id BETWEEN 81000001 AND 81000012
                          AND a.last_transaction_at <> latest.last_occurred_at
                        """))
                .isZero();
        assertThat(
                        count(
                                """
                        SELECT COUNT(*) FROM (
                            SELECT a.account_id
                            FROM account a JOIN ledger_entry le ON le.account_id = a.account_id
                            WHERE a.account_id BETWEEN 81000001 AND 81000012
                            GROUP BY a.account_id, a.balance
                            HAVING a.balance <> SUM(CASE WHEN le.direction = 'DEPOSIT' THEN le.amount ELSE -le.amount END)
                        ) broken
                        """))
                .isZero();
        assertThat(
                        count(
                                """
                        SELECT COUNT(*) FROM (
                            SELECT voucher_no FROM gl_journal_entry
                            WHERE voucher_no = '20260901-OPN-000001'
                            GROUP BY voucher_no
                            HAVING SUM(CASE WHEN dr_cr = 'DEBIT' THEN amount ELSE 0 END)
                                <> SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END)
                        ) broken
                        """))
                .isZero();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
